package com.vmware.cnasg.k8s.watcher;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.StringJoiner;

public class PodWatcher extends AbstractWatcher<Pod> {

    private static final Logger logger = LoggerFactory.getLogger(PodWatcher.class);
    public PodWatcher(KubernetesClient client) {
        super(client);
    }

    @Override
    public void eventReceived(Action action, Pod pod) {
        StringJoiner j = new StringJoiner(",");
        j.add(action.name());
        j.add(pod.getKind());
        j.add(pod.getMetadata().getName());
        j.add(pod.getMetadata().getNamespace());
        j.add(pod.getMetadata().getLabels().toString());
        j.add(pod.getSpec().getContainers().get(0).getImage());
        j.add(pod.getStatus().getHostIP());
        j.add(pod.getStatus().getPodIP());
        logger.info(j.toString());
    }
}
