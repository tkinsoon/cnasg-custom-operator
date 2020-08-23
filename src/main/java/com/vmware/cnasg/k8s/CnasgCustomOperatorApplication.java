package com.vmware.cnasg.k8s;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
public class CnasgCustomOperatorApplication implements CommandLineRunner {

    public static final Logger logger =
            LoggerFactory.getLogger(CnasgCustomOperatorApplication.class);
    private KubernetesClient client;

    public static void main(String[] args) {
        SpringApplication.run(CnasgCustomOperatorApplication.class, args);
    }

    @Override
    public void run(String... arg0) throws Exception {
        KubernetesClient client = new DefaultKubernetesClient();
        CustomResourceDefinitionContext crdContext = new CustomResourceDefinitionContext.Builder()
                .withGroup("dex.coreos.com")
                .withPlural("refreshtokens")
                .withScope("Namespaced")
                .withVersion("v1")
                .build();
        client.customResource(crdContext).watch("kube-system",
                new DexCustomResourceWatcher(client));
        client.services().inAnyNamespace().watch(new ServiceWatcher(client));
    }
}
