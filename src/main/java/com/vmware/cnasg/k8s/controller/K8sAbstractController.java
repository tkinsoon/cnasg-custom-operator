package com.vmware.cnasg.k8s.controller;

import com.vmware.cnasg.k8s.K8sClientProvider;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class K8sAbstractController implements K8sController {

    private static final Logger logger = LoggerFactory.getLogger(K8sAbstractController.class);
    protected K8sClientProvider provider;
    protected K8sControllers controllers;

    protected KubernetesClient getLocalK8sClient() {
        return provider.getLocalK8sClient();
    }

    @Autowired
    public void setProvider(K8sClientProvider provider) {
        this.provider = provider;
    }

    @Autowired
    public void setK8sControllers(K8sControllers controllers) {
        this.controllers = controllers;
    }

    @Override
    public boolean stopController() {
        boolean success = false;
        logger.info("controller stopped");
        return true;
    }
}
