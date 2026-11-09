package com.senne.imttech.weapons;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Een Remote EMP of Remote Frag die nog scherp is terwijl je hem draagt (hoofdstuk 69). Haal je er een met sneak + lege
 * hand van een muur, dan blijft hij van wie hem plakte en luistert hij nog naar diens trigger (jouw keuze: "lege hand,
 * blijft van hem"). Hang je hem ergens anders op, dan hoort zijn trigger waar hij nu hangt (zie {@link MovedCharges}).
 * Drukt de eigenaar terwijl jij hem nog bij je hebt en in bereik bent, dan gaat hij bij jou af.
 *
 * @param trigger   de trigger waar hij bij hoort
 * @param owner     wie hem plakte (als we dat weten)
 * @param ownerName de naam van die speler, voor de teksten
 * @param from      waar hij hing voor iemand hem eraf haalde (zo vergeet de trigger die plek)
 */
public record LiveCharge(UUID trigger, Optional<UUID> owner, String ownerName, Optional<GlobalPos> from) {
    public static final Codec<LiveCharge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("trigger").forGetter(LiveCharge::trigger),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(LiveCharge::owner),
            Codec.STRING.optionalFieldOf("owner_name", "").forGetter(LiveCharge::ownerName),
            GlobalPos.CODEC.optionalFieldOf("from").forGetter(LiveCharge::from)
    ).apply(instance, LiveCharge::new));

    public static final StreamCodec<ByteBuf, LiveCharge> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, LiveCharge::trigger,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), LiveCharge::owner,
            ByteBufCodecs.STRING_UTF8, LiveCharge::ownerName,
            ByteBufCodecs.optional(GlobalPos.STREAM_CODEC), LiveCharge::from,
            LiveCharge::new);
}
