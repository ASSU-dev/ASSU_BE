package com.assu.server.architecture;

import java.util.stream.Stream;
import org.springframework.modulith.core.ApplicationModuleDetectionStrategy;
import org.springframework.modulith.core.JavaPackage;

public class DomainModuleDetectionStrategy implements ApplicationModuleDetectionStrategy {

	@Override
	public Stream<JavaPackage> getModuleBasePackages(JavaPackage basePackage) {
		return basePackage.getDirectSubPackages().stream()
			.filter(javaPackage -> javaPackage.getName().endsWith(".domain"))
			.flatMap(domainPackage -> domainPackage.getDirectSubPackages().stream());
	}
}
