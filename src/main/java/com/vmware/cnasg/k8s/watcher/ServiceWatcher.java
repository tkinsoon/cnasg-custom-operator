package com.vmware.cnasg.k8s.watcher;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.StringJoiner;

import static net.logstash.logback.argument.StructuredArguments.v;

public class ServiceWatcher extends AbstractWatcher<Service> {

    private static final Logger logger = LoggerFactory.getLogger(ServiceWatcher.class);

    public ServiceWatcher(KubernetesClient client) {
        super(client);
    }

    @Override
    public void eventReceived(Action action, Service service) {
        StringJoiner j = new StringJoiner(",");
        j.add(action.name());
        j.add(service.getKind());
        j.add(service.getMetadata().getName());
        String namespace = service.getMetadata().getNamespace();
        if (namespace != null) {
            j.add(namespace);
        }
        logger.info(j.toString());

        logger.info("event received",
                v("kind",service.getKind()),
                v("status",action.name()),
                v("name",service.getMetadata().getName()),
                v("namespace",service.getMetadata().getNamespace()));
    }
}
