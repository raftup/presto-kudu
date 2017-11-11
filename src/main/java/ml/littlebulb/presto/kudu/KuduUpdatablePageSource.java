package ml.littlebulb.presto.kudu;

import com.facebook.presto.spi.Page;
import com.facebook.presto.spi.RecordPageSource;
import com.facebook.presto.spi.UpdatablePageSource;
import com.facebook.presto.spi.block.Block;
import io.airlift.slice.Slice;
import org.apache.kudu.Schema;
import org.apache.kudu.client.*;
import org.apache.kudu.client.SessionConfiguration.FlushMode;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

public class KuduUpdatablePageSource implements UpdatablePageSource {
    private final KuduClientSession clientSession;
    private final KuduTable table;
    private final RecordPageSource inner;

    public KuduUpdatablePageSource(KuduRecordSet recordSet) {
        this.clientSession = recordSet.getClientSession();
        this.table = recordSet.getTable();
        this.inner = new RecordPageSource(recordSet);
    }

    @Override
    public void deleteRows(Block rowIds) {
        Schema schema = table.getSchema();
        KuduSession session = clientSession.newSession();
        session.setFlushMode(FlushMode.AUTO_FLUSH_BACKGROUND);
        try {
            try {
                for (int i = 0; i < rowIds.getPositionCount(); i++) {
                    int len = rowIds.getSliceLength(i);
                    Slice slice = rowIds.getSlice(i, 0, len);
                    PartialRow row = KeyEncoderAccessor.decodePrimaryKey(schema, slice.getBytes());
                    Delete delete = table.newDelete();
                    RowHelper.copyPrimaryKey(schema, row, delete.getRow());
                    session.apply(delete);
                }
            } finally {
                session.close();
            }
        } catch (KuduException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletableFuture<Collection<Slice>> finish() {
        CompletableFuture<Collection<Slice>> cf = new CompletableFuture<>();
        cf.complete(Collections.emptyList());
        return cf;
    }

    @Override
    public long getTotalBytes() {
        return inner.getTotalBytes();
    }

    @Override
    public long getCompletedBytes() {
        return inner.getCompletedBytes();
    }

    @Override
    public long getReadTimeNanos() {
        return inner.getReadTimeNanos();
    }

    @Override
    public boolean isFinished() {
        return inner.isFinished();
    }

    @Override
    public Page getNextPage() {
        return inner.getNextPage();
    }

    @Override
    public long getSystemMemoryUsage() {
        return inner.getSystemMemoryUsage();
    }

    @Override
    public void close() throws IOException {
        inner.close();
    }
}