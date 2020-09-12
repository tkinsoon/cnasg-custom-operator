package com.vmware.cnasg.k8s.watcher;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.logstash.logback.argument.StructuredArguments.v;

public abstract class AbstractWatcher<T> implements Watcher<T> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractWatcher.class);
    private KubernetesClient client;

    public AbstractWatcher(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public void eventReceived(Action action, T t) {
        switch (action) {
            case ADDED:
            case MODIFIED:
            case DELETED:
            case ERROR:
            default:
                logger.info(t.getClass().getName() + " " + action + " " + t.toString());
        }
    }

    @Override
    public void onClose(KubernetesClientException cause) {
        logger.info("watcher closed",
                v("cause",cause));
    }
}
