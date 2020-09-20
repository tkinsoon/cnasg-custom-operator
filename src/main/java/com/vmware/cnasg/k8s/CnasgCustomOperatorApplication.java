package com.vmware.cnasg.k8s;

import com.vmware.cnasg.k8s.controller.CnaSgApplicationController;
import com.vmware.cnasg.k8s.controller.DexNamespaceController;
import com.vmware.cnasg.k8s.controller.K8sControllers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.PreDestroy;

@SpringBootApplication
public class CnasgCustomOperatorApplication implements CommandLineRunner {

    private K8sControllers controllers;
    public static final Logger logger =
            LoggerFactory.getLogger(CnasgCustomOperatorApplication.class);

    @Value("${app.info}")
    private String appInfo;

    @Autowired
    public void setK8sControllers(K8sControllers controllers) {
        this.controllers = controllers;
    }

    public static void main(String[] args) {
        SpringApplication.run(CnasgCustomOperatorApplication.class, args);
    }

    @Override
    public void run(String... arg0) throws Exception {
        logger.info(appInfo);
        controllers.put(DexNamespaceController.class.getSimpleName(), new DexNamespaceController());
        controllers.put(CnaSgApplicationController.class.getSimpleName(), new CnaSgApplicationController());

//        client.pods().inAnyNamespace().watch(new PodWatcher(client));
//        client.services().inAnyNamespace().watch(new ServiceWatcher(client));
//
//        AppRunner appRunner = new AppRunner(provider.getLocalK8sClient());
    }

    @PreDestroy
    public void onDestroy() {
        logger.info("gracefully stop the K8s controllers");
        controllers.stopControllers();
        logger.info("all controllers are stopped");
    }
}
