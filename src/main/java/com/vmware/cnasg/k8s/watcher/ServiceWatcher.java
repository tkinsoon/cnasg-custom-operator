package com.vmware.cnasg.k8s.watcher;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;

import static net.logstash.logback.argument.StructuredArguments.v;

public class ServiceWatcher extends AbstractWatcher<Service> {

    private static final Logger logger = LoggerFactory.getLogger(ServiceWatcher.class);

    public ServiceWatcher(KubernetesClient client) {
        super(client);
    }

    @Override
    public void eventReceived(Action action, Service service) {
        String pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        LocalDate creationDate = LocalDate.from(formatter.parse(service.getMetadata().getCreationTimestamp()));
        Period period = Period.between(creationDate, LocalDate.now());
        if (period.getDays() <= 1) {
            logger.info("event received",
                    v("kind",service.getKind()),
                    v("status",action.name()),
                    v("name",service.getMetadata().getName()),
                    v("namespace",service.getMetadata().getNamespace()));
        } else {
            logger.info("event received, but ignore the event older than a day");
        }
    }
}
