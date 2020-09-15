package com.vmware.cnasg.k8s;

import com.vmware.cnasg.k8s.app.AppRunner;
import com.vmware.cnasg.k8s.watcher.DexCustomResourceWatcher;
import com.vmware.cnasg.k8s.watcher.PodWatcher;
import com.vmware.cnasg.k8s.watcher.ServiceWatcher;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CnasgCustomOperatorApplication implements CommandLineRunner {

    public static final Logger logger =
            LoggerFactory.getLogger(CnasgCustomOperatorApplication.class);
    private KubernetesClient client;

    @Value("${app.info}")
    private String appInfo;
    @Value("${dex.namespace}")
    private String dexNamespace;
    @Value("${dex.api.group}")
    private String dexApiGroup;
    @Value("${dex.api.version}")
    private String dexApiVersion;
    @Value("${dex.customresource}")
    private String dexCustomResource;
    @Value("${dex.scope}")
    private String dexScope;
    @Value("${dex.userrole}")
    private String dexUserRole;
    @Value("${dex.userrolebinding.prefix}")
    private String dexUserRoleBindingPrefix;

    public static void main(String[] args) {
        SpringApplication.run(CnasgCustomOperatorApplication.class, args);
    }

    @Override
    public void run(String... arg0) throws Exception {
        logger.info(appInfo);

        KubernetesClient client = new DefaultKubernetesClient();

        CustomResourceDefinitionContext crdDexContext = new CustomResourceDefinitionContext.Builder()
                .withGroup(dexApiGroup)
                .withPlural(dexCustomResource)
                .withScope(dexScope)
                .withVersion(dexApiVersion)
                .build();
        client.customResource(crdDexContext).watch(dexNamespace,
                new DexCustomResourceWatcher(client,dexUserRole,dexUserRoleBindingPrefix));

        client.pods().inAnyNamespace().watch(new PodWatcher(client));
        client.services().inAnyNamespace().watch(new ServiceWatcher(client));

        AppRunner appRunner = new AppRunner(client);
    }
}
