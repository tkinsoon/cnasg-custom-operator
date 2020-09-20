package com.vmware.cnasg.k8s.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Iterator;

@Component
public class K8sControllers extends HashMap<String,K8sController> {

    private static final Logger logger = LoggerFactory.getLogger(K8sControllers.class);
    private ConfigurableApplicationContext context;

    public void stopControllers() {
        Iterator<K8sController> controllerIterator = values().iterator();
        while (controllerIterator.hasNext()) {
            controllerIterator.next().stopController();
        }
    }

    @Autowired
    public void setConfigurableApplicationContext(ConfigurableApplicationContext context) {
        this.context = context;
    }

    @Override
    public K8sController remove(Object key) {
        BeanDefinitionRegistry registry = (BeanDefinitionRegistry)context.getAutowireCapableBeanFactory();
        for(String beanName : context.getBeanDefinitionNames()){
//            logger.info("beanName: " + beanName);
            if (((String)key).equalsIgnoreCase(beanName)) {
                registry.removeBeanDefinition(beanName);
                logger.info("removed bean[" + beanName + "] from application context");
                break;
            }
        }
        return super.remove(key);
    }
}
