package com.tinkerpop.gremlin.server.handler;

import com.tinkerpop.gremlin.driver.message.ResultType;
import com.tinkerpop.gremlin.driver.message.ResultCode;
import com.tinkerpop.gremlin.server.Settings;
import com.tinkerpop.gremlin.driver.message.RequestMessage;
import com.tinkerpop.gremlin.driver.message.ResponseMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import org.apache.commons.lang.time.StopWatch;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

/**
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
public class IteratorHandler extends ChannelOutboundHandlerAdapter  {
    private static final Logger logger = LoggerFactory.getLogger(IteratorHandler.class);

    private final Settings settings;

    public IteratorHandler(final Settings settings) {
        this.settings = settings;
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) throws Exception {
        if (msg instanceof Pair) {
            try {
                final Pair pair = (Pair) msg;
                final Iterator itty = (Iterator) pair.getValue1();
                final RequestMessage requestMessage = (RequestMessage) pair.getValue0();

                // timer for the total serialization time
                final StopWatch stopWatch = new StopWatch();

                final EventExecutorGroup executorService = ctx.channel().eventLoop().next();
                final Future<?> iteration = executorService.submit((Callable<Void>) () -> {

                    stopWatch.start();

                    // todo: allow definition from the client
                    List<Object> aggregate = new ArrayList<>(settings.resultIterationBatchSize);
                    while (itty.hasNext()) {
                        aggregate.add(itty.next());

                        // send back a page of results if batch size is met or if it's the end of the results being
                        // iterated
                        if (aggregate.size() == settings.resultIterationBatchSize || !itty.hasNext()) {
                            ctx.writeAndFlush(ResponseMessage.create(requestMessage)
                                    .code(ResultCode.SUCCESS)
                                    .result(aggregate)
                                    .contents(ResultType.COLLECTION).build());
                            aggregate = new ArrayList<>(settings.resultIterationBatchSize);
                        }

                        stopWatch.split();
                        if (stopWatch.getSplitTime() > settings.serializedResponseTimeout)
                            throw new TimeoutException("Serialization of the entire response exceeded the serializeResponseTimeout setting");

                        stopWatch.unsplit();
                    }

                    return null;
                });

                iteration.addListener(f->{
                    stopWatch.stop();

                    if (!f.isSuccess()) {
                        final String errorMessage = String.format("Response iteration and serialization exceeded the configured threshold for request [%s] - %s", msg, f.cause().getMessage());
                        logger.warn(errorMessage);
                        ctx.writeAndFlush(ResponseMessage.create(requestMessage).code(ResultCode.SERVER_ERROR_TIMEOUT).result(errorMessage).build());
                    }

                    ctx.writeAndFlush(ResponseMessage.create(requestMessage).code(ResultCode.SUCCESS_TERMINATOR).result(Optional.empty()).build());
                });
            } finally {
                ReferenceCountUtil.release(msg);
            }

        } else {
            ctx.write(msg, promise);
        }
    }
}
