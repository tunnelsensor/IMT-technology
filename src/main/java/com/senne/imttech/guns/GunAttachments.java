package com.senne.imttech.guns;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * De attachments die op een vuurwapen zitten (hoofdstuk 103): een data component op het wapen, bewaard als een rijtje
 * namen ("red_dot_sight"). Op elke plek ({@link AttachmentSlot}) zit er hoogstens één. Een naam die niet (meer) bestaat,
 * wordt bij het inlezen overgeslagen: zo gaat een wapen niet stuk als een attachment later anders heet.
 */
public record GunAttachments(List<Attachment> list) {
    public static final GunAttachments EMPTY = new GunAttachments(List.of());

    public static final Codec<GunAttachments> CODEC = Codec.STRING.listOf().xmap(GunAttachments::ofIds, GunAttachments::ids);

    public static final StreamCodec<ByteBuf, GunAttachments> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).map(GunAttachments::ofIds, GunAttachments::ids);

    public GunAttachments {
        list = List.copyOf(list);
    }

    private static GunAttachments ofIds(List<String> ids) {
        GunAttachments out = EMPTY;
        for (String id : ids) {
            Attachment attachment = Attachment.byId(id);
            if (attachment != null) {
                out = out.with(attachment);
            }
        }
        return out;
    }

    private List<String> ids() {
        List<String> ids = new ArrayList<>();
        for (Attachment attachment : list) {
            ids.add(attachment.id);
        }
        return ids;
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    /** Wat er op deze plek zit, of null. */
    public @Nullable Attachment get(AttachmentSlot slot) {
        for (Attachment attachment : list) {
            if (attachment.slot == slot) {
                return attachment;
            }
        }
        return null;
    }

    /** Hetzelfde met dit attachment erop (wat op zijn plek zat, gaat eraf). */
    public GunAttachments with(Attachment attachment) {
        List<Attachment> out = new ArrayList<>();
        for (Attachment each : list) {
            if (each.slot != attachment.slot) {
                out.add(each);
            }
        }
        out.add(attachment);
        return new GunAttachments(out);
    }

    /** Hetzelfde zonder wat op deze plek zat. */
    public GunAttachments without(AttachmentSlot slot) {
        List<Attachment> out = new ArrayList<>();
        for (Attachment each : list) {
            if (each.slot != slot) {
                out.add(each);
            }
        }
        return out.isEmpty() ? EMPTY : new GunAttachments(out);
    }
}
