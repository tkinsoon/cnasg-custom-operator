package com.vmware.cnasg.k8s;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;

public class ServiceWatcher extends AbstractWatcher<Service> {

    public ServiceWatcher(KubernetesClient client) {
        super(client);
    }

    @Override
    public void eventReceived(Action action, Service service) {
        service.getSpec().getSelector();
        new Gson().toJson(service);
    }
}
