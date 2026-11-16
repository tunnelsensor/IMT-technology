package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import com.senne.imttech.logic.program.LogicProgram;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler (hoofdstuk 119): "dit zijn de RC programs op de server van de basis waar deze RC Car
 * staat". Jouw wens: "zorg dat je de rc car code op de server moet schrijven". De knop Program op het scherm van de auto
 * vraagt dit; jouw spel toont de lijst (client/os/RcCarProgramScreen), en wat je kiest gaat als kopie naar de auto.
 *
 * @param entityId    welke auto
 * @param owner       de naam van zijn eigenaar (voor de knop terug naar zijn scherm)
 * @param remote      true = via de RC Controller
 * @param open        true = de knop Program vroeg erom (open de lijst); false = de lijst na Load onto car (alleen bijwerken
 *                    als hij nog open staat; gevonden door de nalezing: anders sprong hij soms weer open)
 * @param server      van wie de server is ("Senne"), of leeg als er geen is
 * @param problem     waarom er geen lijst is (de auto staat niet in een basis, de server staat uit...), of leeg
 * @param current     de naam van het RC program dat nu op de auto staat ("" = geen: zijn eigen code van vroeger, of niets)
 * @param otherServer true = dat RC program kwam van de server van een andere basis (dan is het niet dat van hier)
 * @param old         true = de server van hier heeft intussen een andere versie van dat programma
 * @param own         de eigen code van een auto van vóór hoofdstuk 119 (alleen lezen, om over te typen), of leeg
 * @param result      wat het laden zei ("Loaded Mining onto Digger", of waarom niet), of leeg als er niet geladen werd
 * @param error       true = het laden mislukte
 * @param programs    de RC programs van die server (hooguit 16, zoveel diagrammen kan een server hebben)
 */
public record RcCarProgramsPayload(int entityId, String owner, boolean remote, boolean open, String server, String problem,
                                   String current, boolean otherServer, boolean old, String own, String result, boolean error,
                                   List<Program> programs) implements CustomPacketPayload {
    public static final Type<RcCarProgramsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "rc_car_programs"));

    /** Zoveel tekens code gaan hooguit mee (een RC program is hooguit {@link LogicProgram#MAX_SOURCE} tekens). */
    public static final int MAX_CODE = LogicProgram.MAX_SOURCE * 2;

    /**
     * Eén RC program: zijn naam en zijn code (die toont het scherm, zodat je ziet wat je laadt). Hoofdstuk 130: is het op
     * slot ({@code locked}, van een SD-kaart), dan is de code leeg: laden mag, lezen niet.
     */
    public record Program(String name, String source, boolean locked) {
        public static final StreamCodec<ByteBuf, Program> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Program::name,
                ByteBufCodecs.stringUtf8(MAX_CODE), Program::source,
                ByteBufCodecs.BOOL, Program::locked,
                Program::new);
    }

    public static final StreamCodec<ByteBuf, RcCarProgramsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.VAR_INT.encode(buf, payload.entityId());
                ByteBufCodecs.stringUtf8(64).encode(buf, payload.owner());
                ByteBufCodecs.BOOL.encode(buf, payload.remote());
                ByteBufCodecs.BOOL.encode(buf, payload.open());
                ByteBufCodecs.stringUtf8(64).encode(buf, payload.server());
                ByteBufCodecs.stringUtf8(512).encode(buf, payload.problem());
                ByteBufCodecs.stringUtf8(64).encode(buf, payload.current());
                ByteBufCodecs.BOOL.encode(buf, payload.otherServer());
                ByteBufCodecs.BOOL.encode(buf, payload.old());
                ByteBufCodecs.stringUtf8(MAX_CODE).encode(buf, payload.own());
                ByteBufCodecs.stringUtf8(512).encode(buf, payload.result());
                ByteBufCodecs.BOOL.encode(buf, payload.error());
                Program.STREAM_CODEC.apply(ByteBufCodecs.list(32)).encode(buf, payload.programs());
            },
            buf -> new RcCarProgramsPayload(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.stringUtf8(64).decode(buf),
                    ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.stringUtf8(64).decode(buf),
                    ByteBufCodecs.stringUtf8(512).decode(buf), ByteBufCodecs.stringUtf8(64).decode(buf),
                    ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.stringUtf8(MAX_CODE).decode(buf),
                    ByteBufCodecs.stringUtf8(512).decode(buf), ByteBufCodecs.BOOL.decode(buf),
                    new ArrayList<>(Program.STREAM_CODEC.apply(ByteBufCodecs.list(32)).decode(buf))));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
