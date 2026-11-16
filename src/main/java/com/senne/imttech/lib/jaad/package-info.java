/**
 * JAAD, the AAC decoder by in-somnia (https://sourceforge.net/projects/jaadec/), version 0.8.7 of the fork by
 * DV8FromTheWorld and Stephan Fuhrmann (https://github.com/sfuhrm/JAADec). JAAD is in the public domain (see
 * THIRD-PARTY-LICENSES.txt), so its source can live inside this mod.
 *
 * Only the AAC decoder ({@code aac}) and the reader for raw .aac files ({@code adts}) are here; the packages were renamed
 * from {@code net.sourceforge.jaad} so they never clash with another mod that ships JAAD. Changes for I.M.T Technology
 * (chapter 126): Temporal Noise Shaping implemented (aac/tools/TNS: it was an empty TODO, so most mp4 sounds came out
 * distorted), pulse data applied and a proper random generator for PNS (aac/syntax/ICStream), and two bit-counting
 * fixes in fill elements (aac/syntax/FIL: dynamic range info was miscounted by 4 bits, and leftover bits were skipped by
 * the wrong amount). SBR is always switched off by the caller (alarm/audio/Mp4Decoder reads only the AAC core). The mp4
 * container is read by our own alarm/audio/Mp4Demuxer instead of JAAD's mp4 package.
 *
 * Nederlands: dit is de AAC-decoder van JAAD (publiek domein), met de stukken die in JAAD nooit af waren, afgemaakt. Hij
 * leest het geluid van mp4-, m4a- en aac-bestanden voor de Alarm Speaker (zie alarm/audio/Mp4Decoder).
 */
package com.senne.imttech.lib.jaad;
