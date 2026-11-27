package com.assu.server.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

import com.assu.server.domain.backoffice.annotation.BackofficeAudited;
import com.assu.server.domain.backoffice.entity.enums.BackofficeAuditType;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;

@AnalyzeClasses(packages = "com.assu.server", importOptions = ImportOption.DoNotIncludeTests.class)
class ConventionArchitectureTest {

	private static final String DOMAIN = "com.assu.server.domain..";
	private static final String DTO = "com.assu.server.domain.*.dto..";
	private static final String SERVICE = "com.assu.server.domain.*.service..";

	private static final DescribedPredicate<JavaClass> RECORD_OR_ENUM_OR_INTERFACE =
		DescribedPredicate.describe("record, enum or interface",
			javaClass -> javaClass.isRecord() || javaClass.isEnum() || javaClass.isInterface());

	private static final ArchCondition<JavaClass> BE_RECORD_OR_HOLDER_OF_RECORDS =
		new ArchCondition<>("be a record, or a holder class without fields") {
			@Override
			public void check(JavaClass javaClass, ConditionEvents events) {
				boolean satisfied = RECORD_OR_ENUM_OR_INTERFACE.test(javaClass)
					|| javaClass.getFields().stream().allMatch(field -> field.getModifiers().contains(JavaModifier.STATIC));
				events.add(new SimpleConditionEvent(javaClass, satisfied,
					javaClass.getName() + " is not a record and declares instance fields"));
			}
		};

	@ArchTest
	static final ArchRule entities_should_not_have_public_setters = methods()
		.that().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
		.and().haveNameMatching("set[A-Z].*")
		.should().notBePublic()
		.allowEmptyShould(true)
		.because("엔티티 상태는 의도가 드러나는 행위 메서드로만 변경한다");

	@ArchTest
	static final ArchRule top_level_dtos_should_be_records_or_holders = classes()
		.that().resideInAPackage(DTO)
		.and().areTopLevelClasses()
		.should(BE_RECORD_OR_HOLDER_OF_RECORDS)
		.because("DTO는 record로 작성하고, 묶음 클래스는 내부 record만 담는다");

	@ArchTest
	static final ArchRule nested_dtos_should_be_records = classes()
		.that().resideInAPackage(DTO)
		.and().areMemberClasses()
		.and().haveSimpleNameNotEndingWith("Builder")
		.should(new ArchCondition<>("be a record, enum or interface") {
			@Override
			public void check(JavaClass javaClass, ConditionEvents events) {
				events.add(new SimpleConditionEvent(javaClass, RECORD_OR_ENUM_OR_INTERFACE.test(javaClass),
					javaClass.getName() + " is not a record"));
			}
		})
		.because("DTO는 record로 작성한다");

	@ArchTest
	static final ArchRule top_level_dtos_should_end_with_dto = classes()
		.that().resideInAPackage(DTO)
		.and().areTopLevelClasses()
		.and().areNotEnums()
		.and().areNotInterfaces()
		.should().haveSimpleNameEndingWith("DTO")
		.because("DTO 클래스명은 ~DTO로 통일한다");

	@ArchTest
	static final ArchRule domain_enums_should_reside_in_enums_package = classes()
		.that().resideInAPackage(DOMAIN)
		.and().areEnums()
		.and().areTopLevelClasses()
		.should().resideInAPackage("..enums..")
		.because("도메인 enum은 entity/enums 패키지에 둔다");

	@ArchTest
	static final ArchRule converter_classes_should_not_exist = noClasses()
		.that().resideInAPackage(DOMAIN)
		.should().resideInAPackage("..converter..")
		.orShould().haveSimpleNameEndingWith("Converter")
		.because("변환 로직은 별도 Converter 없이 DTO record의 정적 팩토리 메서드에 둔다");

	@ArchTest
	static final ArchRule no_field_injection = noFields()
		.should().beAnnotatedWith(Autowired.class)
		.orShould().beAnnotatedWith("jakarta.inject.Inject")
		.because("의존성은 생성자 주입(@RequiredArgsConstructor)으로 받는다");

	@ArchTest
	static final ArchRule no_java_util_logging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

	@ArchTest
	static final ArchRule no_standard_streams = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

	@ArchTest
	static final ArchRule domain_should_throw_general_exception = noClasses()
		.that().resideInAPackage(DOMAIN)
		.should().callConstructor(RuntimeException.class, String.class)
		.orShould().callConstructor(RuntimeException.class)
		.orShould().callConstructor(IllegalArgumentException.class, String.class)
		.orShould().callConstructor(IllegalArgumentException.class)
		.orShould().callConstructor(IllegalStateException.class, String.class)
		.orShould().callConstructor(IllegalStateException.class)
		.because("예외는 ErrorStatus 기반 GeneralException으로 던져 공통 응답 형식을 지킨다");

	@ArchTest
	static final ArchRule backoffice_audit_type_should_match_http_method = methods()
		.that().areAnnotatedWith(BackofficeAudited.class)
		.should(new ArchCondition<>("use READ audit type only on GET handlers") {
			@Override
			public void check(JavaMethod method, ConditionEvents events) {
				boolean isGet = method.isAnnotatedWith(GetMapping.class);
				BackofficeAuditType type = method.getAnnotationOfType(BackofficeAudited.class).type();
				boolean satisfied = isGet == (type == BackofficeAuditType.READ);
				events.add(new SimpleConditionEvent(method, satisfied,
					method.getFullName() + " is " + (isGet ? "GET" : "not GET") + " but audit type is " + type));
			}
		})
		.because("조회 감사는 GET에만 type = READ로, CUD 감사는 기본값 WRITE로 구분한다");

	@ArchTest
	static final ArchRule should_not_use_jakarta_transactional = noClasses()
		.should().dependOnClassesThat().haveFullyQualifiedName("jakarta.transaction.Transactional")
		.because("트랜잭션은 Spring @Transactional로 통일한다");

	@ArchTest
	static final ArchRule transactional_classes_should_reside_in_service = classes()
		.that().areAnnotatedWith(Transactional.class)
		.should().resideInAPackage(SERVICE)
		.because("트랜잭션 경계는 service 계층에서 관리한다");

	@ArchTest
	static final ArchRule transactional_methods_should_reside_in_service = methods()
		.that().areAnnotatedWith(Transactional.class)
		.should().beDeclaredInClassesThat().resideInAPackage(SERVICE)
		.because("트랜잭션 경계는 service 계층에서 관리한다");

	@ArchTest
	static final ArchRule transactional_methods_should_be_public = methods()
		.that().areAnnotatedWith(Transactional.class)
		.should().bePublic()
		.because("private 메서드의 @Transactional은 프록시가 적용되지 않아 무시된다");
}
