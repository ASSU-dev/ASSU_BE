package com.assu.server.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.assu.server", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerArchitectureTest {

	private static final String CONTROLLER = "com.assu.server.domain.*.controller..";
	private static final String SERVICE = "com.assu.server.domain.*.service..";
	private static final String REPOSITORY = "com.assu.server.domain.*.repository..";
	private static final String ENTITY = "com.assu.server.domain.*.entity..";
	private static final String DTO = "com.assu.server.domain.*.dto..";

	private static final String ALLOWED_GLOBAL_TO_DOMAIN = "com\\.assu\\.server\\.("
		+ "global\\.util\\.PrincipalDetails"
		+ "|global\\.config\\.SecurityConfig"
		+ "|global\\.config\\.AuthProviderConfig"
		+ "|global\\.config\\.IpRateLimitInterceptor"
		+ "|global\\.config\\.WebSocketConfig"
		+ "|infra\\.firebase\\.FcmClient"
		+ ")(\\$.*)?";

	@ArchTest
	static final ArchRule controllers_should_not_access_repositories = noClasses()
		.that().resideInAPackage(CONTROLLER)
		.should().dependOnClassesThat().resideInAPackage(REPOSITORY)
		.because("controller는 service를 통해서만 데이터에 접근한다");

	@ArchTest
	static final ArchRule services_should_not_depend_on_controllers = noClasses()
		.that().resideInAPackage(SERVICE)
		.should().dependOnClassesThat().resideInAPackage(CONTROLLER)
		.because("하위 레이어는 상위 레이어를 알지 않는다");

	@ArchTest
	static final ArchRule repositories_should_not_depend_on_services_or_controllers = noClasses()
		.that().resideInAPackage(REPOSITORY)
		.should().dependOnClassesThat().resideInAnyPackage(SERVICE, CONTROLLER)
		.because("하위 레이어는 상위 레이어를 알지 않는다");

	@ArchTest
	static final ArchRule entities_should_not_depend_on_upper_layers = noClasses()
		.that().resideInAPackage(ENTITY)
		.should().dependOnClassesThat().resideInAnyPackage(DTO, SERVICE, CONTROLLER, REPOSITORY)
		.because("엔티티는 요청·응답 형식과 상위 레이어를 알지 않는다");

	@ArchTest
	static final ArchRule dtos_should_not_depend_on_services_controllers_or_repositories = noClasses()
		.that().resideInAPackage(DTO)
		.should().dependOnClassesThat().resideInAnyPackage(SERVICE, CONTROLLER, REPOSITORY)
		.because("DTO는 데이터 전달과 변환만 담당한다");

	@ArchTest
	static final ArchRule common_domain_should_not_depend_on_other_domains = noClasses()
		.that().resideInAPackage("com.assu.server.domain.common..")
		.should().dependOnClassesThat(resideInAPackage("com.assu.server.domain..")
			.and(resideOutsideOfPackage("com.assu.server.domain.common..")))
		.because("common은 모든 도메인이 공유하므로 특정 도메인에 의존하지 않는다");

	@ArchTest
	static final ArchRule global_and_infra_should_not_depend_on_domain = noClasses()
		.that().resideInAnyPackage("com.assu.server.global..", "com.assu.server.infra..")
		.and().haveNameNotMatching(ALLOWED_GLOBAL_TO_DOMAIN)
		.should().dependOnClassesThat().resideInAPackage("com.assu.server.domain..")
		.because("공통 계층은 도메인을 알지 않는다 (인증·WebSocket·FCM 설정 6개 클래스만 허용)");
}
