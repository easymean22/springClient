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
import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;



@RestController
@SpringBootApplication
public class DemoApplication {

	private static final Logger logger = LoggerFactory.getLogger(DemoApplication.class);
	
	@RequestMapping("/test") //pod 목록 받아오는 test
    void test() {
    	try (KubernetesClient k8s = new KubernetesClientBuilder().build()) {
            // Print names of all pods in specified namespace
    		System.setProperty("kubernetes.auth.tryKubeConfig", "true");
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
    	System.setProperty("kubernetes.auth.tryKubeConfig", "true");
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
    
   
    String getTokenString(KubernetesClient k8s, String tokenName) throws UnsupportedEncodingException {
    	//Secret secret = k8s.secrets().inNamespace("spring").withName(tokenName).get();
    	Secret secret = k8s.secrets().withName(tokenName).get();
    	String encodeToken = secret.getData().get("token");
    	Decoder decoder = Base64.getDecoder();
    	byte[] decodedBytes2 = decoder.decode(encodeToken);
    	String token = new String(decodedBytes2, "UTF-8");
    	return token;
    }
    
    String getCaCrt(KubernetesClient k8s, String tokenName) throws UnsupportedEncodingException {
    	//Secret secret = k8s.secrets().inNamespace("spring").withName(tokenName).get();
    	Secret secret = k8s.secrets().withName(tokenName).get();
    	String encodeCrt = secret.getData().get("ca.crt");
    	Decoder decoder = Base64.getDecoder();
    	byte[] decodedBytes2 = decoder.decode(encodeCrt);
    	String crt = new String(decodedBytes2, "UTF-8");
    	return crt;
    }
    
    String getNamespace(KubernetesClient k8s, String tokenName)  throws UnsupportedEncodingException {
    	//Secret secret = k8s.secrets().inNamespace("spring").withName(tokenName).get();
    	Secret secret = k8s.secrets().withName(tokenName).get();
    	String encodeNamespace = secret.getData().get("namespace");
    	Decoder decoder = Base64.getDecoder();
    	byte[] decodedBytes2 = decoder.decode(encodeNamespace);
    	String namespace = new String(decodedBytes2, "UTF-8");
    	return namespace;
    }
    
    
    @RequestMapping("/setvpn") //vpn_cr.yaml 만들어주는 api
    void setVPN() {
    	KubernetesClient k8s = new KubernetesClientBuilder().build();
    	String token;
    	String crt;
    	String namespace;
    	String tokenName = this.getTokenName(k8s,"springservice1");
    	
    	try{
    		token = this.getTokenString(k8s, tokenName);
    		Path path = Paths.get("./token");
    		Files.write(path, token.getBytes());
    	} 
    	catch(UnsupportedEncodingException e) {
    	}
    	catch(IOException e) {
    		e.printStackTrace();
    	};
    	
    	try{
    		crt = this.getCaCrt(k8s, tokenName);
    		Path path = Paths.get("./ca.crt");
    		Files.write(path, crt.getBytes());
    	} catch(UnsupportedEncodingException e) {
    	}
    	catch(IOException e) {
    		e.printStackTrace();
    	};
    	
    	try{
    		namespace = this.getNamespace(k8s, tokenName);
    		Path path = Paths.get("./namespace");
    		Files.write(path, namespace.getBytes());
    	} catch(UnsupportedEncodingException e) {
    	}
    	catch (IOException e) {
    		e.printStackTrace();
    	};
    	
    	//System.setProperty("kubernetes.disable.autoConfig", "false");
    	System.setProperty("kubernetes.master", "https://192.168.56.10:6443");
    	System.setProperty("kubernetes.auth.tryKubeConfig", "false");
    	System.setProperty("kubernetes.auth.serviceAccount.token", "./token");
    	System.setProperty("kubernetes.certs.ca.file", "./ca.crt");
    	System.setProperty("kubenamespace", "./namespace");
    	ConfigBuilder configBuilder = new ConfigBuilder().withMasterUrl("https://192.168.56.10:6443");
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