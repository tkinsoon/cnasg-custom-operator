package com.vmware.cnasg.k8s.watcher;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;

import static net.logstash.logback.argument.StructuredArguments.v;

public class PodWatcher extends AbstractWatcher<Pod> {

    private static final Logger logger = LoggerFactory.getLogger(PodWatcher.class);
    public PodWatcher(KubernetesClient client) {
        super(client);
    }

    @Override
    public void eventReceived(Action action, Pod pod) {

        String pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        LocalDate creationDate = LocalDate.from(formatter.parse(pod.getMetadata().getCreationTimestamp()));
        Period period = Period.between(creationDate, LocalDate.now());
        if (period.getDays() <= 1) {
            logger.info("event received",
                    v("kind",pod.getKind()),
                    v("status",action.name()),
                    v("name",pod.getMetadata().getName()),
                    v("namespace",pod.getMetadata().getNamespace()),
                    v("labels",pod.getMetadata().getLabels().toString()),
                    v("image",pod.getSpec().getContainers().get(0).getImage()),
                    v("hostIp",pod.getStatus().getHostIP()),
                    v("podIp",pod.getStatus().getPodIP()));
        } else {
            logger.info("event received, but ignore the event older than a day");
        }

    }
}
