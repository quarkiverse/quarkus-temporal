package io.quarkiverse.temporal.graal.netty.runtime;

import io.grpc.netty.shaded.io.netty.channel.ChannelHandlerAdapter;

/**
 * Custom marker interface used to do faster {@link ChannelHandlerAdapter#isSharable()} checks as an interface instanceof
 * is much faster than looking up an annotation
 */
public interface NettySharable {
}
