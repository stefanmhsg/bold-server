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

    private final MazeUpdateListener updateListener;

    public MazeNotifyingSail(NotifyingSail baseSail) {
        super(baseSail);
        this.updateListener = new MazeUpdateListener();
    }

    @Override
    public NotifyingSailConnection getConnection() throws SailException {
        // Base connection from the wrapped store
        NotifyingSailConnection baseConn = (NotifyingSailConnection) super.getConnection();

        // Wrap so we can control listeners without touching the base
        NotifyingSailConnectionWrapper wrapped = new NotifyingSailConnectionWrapper(baseConn);

        // Attach our listener so it sees every statement add or remove
        wrapped.addConnectionListener(updateListener);

        return wrapped;
    }
}
