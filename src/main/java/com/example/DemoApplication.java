package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.Secret;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@RestController
@SpringBootApplication
public class DemoApplication {

	private static final Logger logger = LoggerFactory.getLogger(DemoApplication.class);
	
	@RequestMapping("/test") //pod 목록 받아오는 test
    void test() {
    	try (KubernetesClient k8s = new KubernetesClientBuilder().build()) {
            // Print names of all pods in specified namespace
            k8s.pods().inNamespace("default").list()
              .getItems()
              .stream()
              .map(Pod::getMetadata)
              .map(ObjectMeta::getName)
              .forEach(logger::info);
        }
	}
	

    @RequestMapping("/serviceaccount") //serviceAccount1을 만들어 주는 api
    void createServiceAccount() {
    	ServiceAccount serviceAccount1 = new ServiceAccountBuilder()
    			  .withNewMetadata().withName("springservice1").endMetadata()
    			  .withAutomountServiceAccountToken(false)
    			  .build();
    	try (final KubernetesClient k8s = new KubernetesClientBuilder().build()) {
    		k8s.serviceAccounts().inNamespace("spring").create(serviceAccount1);
    	}
    }
    
    
    String getTokenName(KubernetesClient k8s, String serviceAccountName) {
    	List<String> tokenlist = k8s.serviceAccounts().inNamespace("spring").withName(serviceAccountName).get(). 
    	getSecrets(). //List<ObjectReference> 반환
    	stream().
    	map(ObjectReference::getName).
    	toList();
    	return tokenlist.get(0);
    }
    
   
    String getTokenString(KubernetesClient k8s, String tokenName) {
    	Secret secret = k8s.secrets().inNamespace("spring").withName(tokenName).get();
    	String token = secret.getData().get("ca.crt");
    	return token;
    }
    
    
    @RequestMapping("/setvpn") //vpn_cr.yaml 만들어주는 api
    void setVPN() {
    	String token;
    	try (final KubernetesClient k8s = new KubernetesClientBuilder().build()) {
        	String tokenName = this.getTokenName(k8s,"springservice1");
        	token = this.getTokenString(k8s, tokenName);
    	}
    	
    	//System.setProperty("kubernetes.disable.autoConfig", "false");
    	System.setProperty("kubernetes.auth.tryKubeConfig", "false");
    	//System.setProperty("kubernetes.auth.tryServiceAccount", "true");
    	//System.setProperty("kubernetes.auth.token", token);
    	ConfigBuilder configBuilder = new ConfigBuilder().withMasterUrl("https://192.168.56.10:6443")
    			.withOauthToken(token);
    	Config config = configBuilder.build();
    	
    	
    	try (KubernetesClient springUser = new KubernetesClientBuilder().withConfig(config).build()) {
    		ResourceDefinitionContext context = new ResourceDefinitionContext
    	        .Builder()
    	        .withGroup("network.tmaxanc.com")
    	        .withKind("VPN")
    	        .withPlural("vpns")
    	        .withNamespaced(true)
    	        .withVersion("v1")
    	        .build();// Load from Yaml
    		Resource<GenericKubernetesResource> vpnObject = springUser.genericKubernetesResources(context)
    				.load(DemoApplication.class.getResourceAsStream("./vpn_cr.yaml"));  // Create Custom Resource
    		vpnObject.create();
    	}
    }
    
    
    public static void main(String[] args) {
      SpringApplication.run(DemoApplication.class, args);
    }
}