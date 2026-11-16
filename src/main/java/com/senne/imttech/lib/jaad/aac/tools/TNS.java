package com.senne.imttech.lib.jaad.aac.tools;

import com.senne.imttech.lib.jaad.aac.AACException;
import com.senne.imttech.lib.jaad.aac.SampleFrequency;
import com.senne.imttech.lib.jaad.aac.syntax.BitStream;
import com.senne.imttech.lib.jaad.aac.syntax.Constants;
import com.senne.imttech.lib.jaad.aac.syntax.ICSInfo;
import com.senne.imttech.lib.jaad.aac.syntax.ICStream;

/**
 * Temporal Noise Shaping
 * @author in-somnia
 *
 * I.M.T Technology (chapter 126): in JAAD only the bitstream part was written; process() was an empty TODO, so every
 * stream that uses TNS (ffmpeg's AAC encoder does, and most encoders use it on sharp sounds) decoded wrongly. This class
 * now follows ISO/IEC 14496-3, 4.6.9: the raw coefficients are kept as signed integers, inverse quantized with the
 * standard formula, turned into LPC coefficients, and run as an all-pole filter over the spectral lines of each filter's
 * band range (process with decode == false), or as the all-zero filter for long-term prediction (decode == true).
 * (JAAD's TNSTables had the opposite sign of the standard and are no longer used.)
 *
 * Nederlands: in JAAD was dit stuk nooit afgemaakt; daardoor klonk elk mp4-geluid dat TNS gebruikt vervormd. Nu werkt het
 * zoals de AAC-norm het voorschrijft.
 */
public class TNS implements Constants {

	private static final int TNS_MAX_ORDER = 20;
	private static final int[] SHORT_BITS = {1, 4, 3}, LONG_BITS = {2, 6, 5};
	/**
	 * The highest scale factor band TNS may touch, per sampling frequency index, for long and short windows (Main and LC
	 * profiles; ISO/IEC 14496-3 table 4.139, the same numbers as FAAD2's tns_sbf_max).
	 */
	private static final int[][] MAX_BANDS = {
		{31, 9}, {31, 9}, {34, 10}, {40, 14}, {42, 14}, {51, 14}, {46, 14}, {46, 14},
		{42, 14}, {42, 14}, {42, 14}, {39, 14}, {39, 14}, {0, 0}, {0, 0}, {0, 0}
	};
	//bitstream
	private final int[] nFilt;
	private final int[] coefRes;
	private final int[][] length, order, coefCompress;
	private final boolean[][] direction;
	private final int[][][] coef;

	public TNS() {
		nFilt = new int[8];
		coefRes = new int[8];
		length = new int[8][4];
		direction = new boolean[8][4];
		order = new int[8][4];
		coefCompress = new int[8][4];
		coef = new int[8][4][TNS_MAX_ORDER];
	}

	public void decode(BitStream in, ICSInfo info) throws AACException {
		final int windowCount = info.getWindowCount();
		final int[] bits = info.isEightShortFrame() ? SHORT_BITS : LONG_BITS;

		for(int w = 0; w<windowCount; w++) {
			if((nFilt[w] = in.readBits(bits[0]))!=0) {
				coefRes[w] = in.readBit()+3; //3 or 4 bits

				for(int filt = 0; filt<nFilt[w]; filt++) {
					length[w][filt] = in.readBits(bits[1]);

					if((order[w][filt] = in.readBits(bits[2]))>TNS_MAX_ORDER) throw new AACException("TNS filter out of range: "+order[w][filt]);
					else if(order[w][filt]!=0) {
						direction[w][filt] = in.readBool();
						coefCompress[w][filt] = in.readBit();
						final int coefLen = coefRes[w]-coefCompress[w][filt];

						for(int i = 0; i<order[w][filt]; i++) {
							//a signed number in coefLen bits (two's complement)
							final int raw = in.readBits(coefLen);
							coef[w][filt][i] = raw>=(1<<(coefLen-1)) ? raw-(1<<coefLen) : raw;
						}
					}
				}
			}
		}
	}

	/**
	 * Applies TNS to the spectral data of one channel.
	 *
	 * @param decode false: the decoder's inverse (all-pole) filter; true: the encoder's (all-zero) filter, used by
	 * long-term prediction
	 */
	public void process(ICStream ics, float[] spec, SampleFrequency sf, boolean decode) {
		final ICSInfo info = ics.getInfo();
		final int windowCount = info.getWindowCount();
		final boolean shortFrame = info.isEightShortFrame();
		final int[] swbOffsets = info.getSWBOffsets();
		final int swbOffsetMax = info.getSWBOffsetMax();
		final int maxSFB = info.getMaxSFB();
		final int swbCount = info.getSWBCount();
		final int windowLength = spec.length/(shortFrame ? 8 : 1);
		final int index = sf.getIndex();
		final int maxBands = index>=0&&index<MAX_BANDS.length ? MAX_BANDS[index][shortFrame ? 1 : 0] : 0;

		for(int w = 0; w<windowCount; w++) {
			int bottom = swbCount;
			for(int f = 0; f<nFilt[w]; f++) {
				final int top = bottom;
				bottom = Math.max(top-length[w][f], 0);
				final int tnsOrder = Math.min(order[w][f], TNS_MAX_ORDER);
				if(tnsOrder==0) continue;

				final float[] lpc = lpc(coef[w][f], tnsOrder, coefRes[w]);

				int start = Math.min(Math.min(bottom, maxBands), maxSFB);
				start = Math.min(swbOffsets[start], swbOffsetMax);
				int end = Math.min(Math.min(top, maxBands), maxSFB);
				end = Math.min(swbOffsets[end], swbOffsetMax);
				final int size = end-start;
				if(size<=0) continue;

				final int base = w*windowLength;
				final int inc;
				final int first;
				if(direction[w][f]) {
					inc = -1;
					first = base+end-1;
				}
				else {
					inc = 1;
					first = base+start;
				}
				if(decode) maFilter(spec, first, size, inc, lpc, tnsOrder);
				else arFilter(spec, first, size, inc, lpc, tnsOrder);
			}
		}
	}

	/** Inverse quantization (ISO/IEC 14496-3, 4.6.9.3) and the conversion of the reflection coefficients to LPC. */
	private static float[] lpc(int[] coef, int order, int coefRes) {
		final double iqfac = ((1<<(coefRes-1))-0.5)/(Math.PI/2.0);
		final double iqfacM = ((1<<(coefRes-1))+0.5)/(Math.PI/2.0);
		final float[] tmp = new float[order];
		for(int i = 0; i<order; i++) {
			tmp[i] = (float) Math.sin(coef[i]/(coef[i]>=0 ? iqfac : iqfacM));
		}
		final float[] a = new float[order+1];
		final float[] b = new float[order+1];
		a[0] = 1.0f;
		for(int m = 1; m<=order; m++) {
			for(int i = 1; i<m; i++) {
				b[i] = a[i]+tmp[m-1]*a[m-i];
			}
			for(int i = 1; i<m; i++) {
				a[i] = b[i];
			}
			a[m] = tmp[m-1];
		}
		return a;
	}

	/** y(n) = x(n) - lpc[1]*y(n-1) - ... - lpc[order]*y(n-order), in place; the filter state starts at zero. */
	private static void arFilter(float[] spec, int first, int size, int inc, float[] lpc, int order) {
		final float[] state = new float[order];
		int pos = first;
		for(int i = 0; i<size; i++, pos += inc) {
			float y = spec[pos];
			for(int j = 0; j<order; j++) {
				y -= state[j]*lpc[j+1];
			}
			for(int j = order-1; j>0; j--) {
				state[j] = state[j-1];
			}
			state[0] = y;
			spec[pos] = y;
		}
	}

	/** y(n) = x(n) + lpc[1]*x(n-1) + ... + lpc[order]*x(n-order), in place; the filter state starts at zero. */
	private static void maFilter(float[] spec, int first, int size, int inc, float[] lpc, int order) {
		final float[] state = new float[order];
		int pos = first;
		for(int i = 0; i<size; i++, pos += inc) {
			final float x = spec[pos];
			float y = x;
			for(int j = 0; j<order; j++) {
				y += state[j]*lpc[j+1];
			}
			for(int j = order-1; j>0; j--) {
				state[j] = state[j-1];
			}
			state[0] = x;
			spec[pos] = y;
		}
	}
}
