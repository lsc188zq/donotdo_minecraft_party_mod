package com.partygame.network;

import com.partygame.PartyGame;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

// 服务端 → 客户端：轮数/阶段/倒计时、自己的必做、个性化分数/禁忌视图
public record SyncStatesPayload(
        int round, String phase, int countdownSeconds,
        String myMustDo, boolean myMustDoDone,
        List<Row> rows) implements CustomPacketPayload {
    public static final Type<SyncStatesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PartyGame.MODID, "sync_states"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncStatesPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, SyncStatesPayload::round,
                    ByteBufCodecs.STRING_UTF8, SyncStatesPayload::phase,
                    ByteBufCodecs.INT, SyncStatesPayload::countdownSeconds,
                    ByteBufCodecs.STRING_UTF8, SyncStatesPayload::myMustDo,
                    ByteBufCodecs.BOOL, SyncStatesPayload::myMustDoDone,
                    Row.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncStatesPayload::rows,
                    SyncStatesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // 一行数据：名字、禁忌文本（自己为 ???）、剩余命数、分数、是否是自己、必做是否完成
    public record Row(String name, String tabooText, int lives, int score, boolean self, boolean mustDoDone) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, Row::name,
                        ByteBufCodecs.STRING_UTF8, Row::tabooText,
                        ByteBufCodecs.INT, Row::lives,
                        ByteBufCodecs.INT, Row::score,
                        ByteBufCodecs.BOOL, Row::self,
                        ByteBufCodecs.BOOL, Row::mustDoDone,
                        Row::new);
    }
}
