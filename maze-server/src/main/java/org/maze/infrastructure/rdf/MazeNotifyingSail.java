package org.maze.infrastructure.rdf;

import org.eclipse.rdf4j.sail.NotifyingSail;
import org.eclipse.rdf4j.sail.NotifyingSailConnection;
import org.eclipse.rdf4j.sail.SailException;
import org.eclipse.rdf4j.sail.helpers.NotifyingSailConnectionWrapper;
import org.eclipse.rdf4j.sail.helpers.NotifyingSailWrapper;

/**
 * Sail wrapper that attaches a MazeUpdateListener to every connection.
 */
public class MazeNotifyingSail extends NotifyingSailWrapper {

    public MazeNotifyingSail(NotifyingSail baseSail) {
        super(baseSail);
    }

    @Override
    public NotifyingSailConnection getConnection() throws SailException {
        // Base connection from the wrapped store
        NotifyingSailConnection baseConn = (NotifyingSailConnection) super.getConnection();

        // Return our custom wrapper that handles commit/rollback hooks
        return new MazeConnectionWrapper(baseConn);
    }

    /**
     * Inner class to intercept transaction boundaries.
     */
    private static class MazeConnectionWrapper extends NotifyingSailConnectionWrapper {
        
        private final MazeUpdateListener listener;

        public MazeConnectionWrapper(NotifyingSailConnection wrapped) {
            super(wrapped);
            // Create a NEW listener for this specific connection/transaction
            this.listener = new MazeUpdateListener();
            addConnectionListener(this.listener);
        }

        @Override
        public void commit() throws SailException {
            // 1. Let the store commit the data
            super.commit();
            // 2. If successful, flush the aggregated events
            listener.onCommit();
        }

        @Override
        public void rollback() throws SailException {
            super.rollback();
            listener.onRollback();
        }
    }
}
