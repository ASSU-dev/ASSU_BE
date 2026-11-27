package com.assu.server.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DomainCycleArchitectureTest {

	private static final Path BASELINE = Path.of("src/test/resources/architecture/domain-cycle-baseline.txt");
	private static final Pattern DOMAIN = Pattern.compile("^com\\.assu\\.server\\.domain\\.(\\w+)\\..*");

	@Test
	void domainCyclesShouldNotGrow() throws IOException {
		Set<String> cyclicEdges = findCyclicEdges(collectDomainEdges());

		if (Boolean.getBoolean("archunit.domainCycles.update")) {
			Files.write(BASELINE, cyclicEdges);
			return;
		}

		Set<String> baseline = new TreeSet<>(Files.readAllLines(BASELINE));
		Set<String> newCycles = new TreeSet<>(cyclicEdges);
		newCycles.removeAll(baseline);
		Set<String> resolvedCycles = new TreeSet<>(baseline);
		resolvedCycles.removeAll(cyclicEdges);

		assertThat(newCycles)
			.as("새로 생긴 도메인 간 순환 의존입니다. 의존 방향을 바꾸거나 이벤트·ID 참조로 끊어 주세요")
			.isEmpty();
		assertThat(resolvedCycles)
			.as("순환이 해소된 의존입니다. %s에서 해당 줄을 지워 주세요", BASELINE)
			.isEmpty();
	}

	private Map<String, Set<String>> collectDomainEdges() {
		JavaClasses classes = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages("com.assu.server.domain");
		Map<String, Set<String>> edges = new TreeMap<>();
		for (JavaClass javaClass : classes) {
			Optional<String> source = domainOf(javaClass.getName());
			if (source.isEmpty()) {
				continue;
			}
			for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
				domainOf(dependency.getTargetClass().getName())
					.filter(target -> !target.equals(source.get()))
					.ifPresent(target -> edges.computeIfAbsent(source.get(), key -> new TreeSet<>()).add(target));
			}
		}
		return edges;
	}

	private Set<String> findCyclicEdges(Map<String, Set<String>> edges) {
		Set<String> cyclicEdges = new TreeSet<>();
		edges.forEach((source, targets) -> targets.stream()
			.filter(target -> reaches(edges, target, source))
			.forEach(target -> cyclicEdges.add(source + " -> " + target)));
		return cyclicEdges;
	}

	private boolean reaches(Map<String, Set<String>> edges, String from, String to) {
		Set<String> visited = new HashSet<>();
		Deque<String> queue = new ArrayDeque<>(List.of(from));
		while (!queue.isEmpty()) {
			String current = queue.poll();
			if (current.equals(to)) {
				return true;
			}
			if (visited.add(current)) {
				queue.addAll(edges.getOrDefault(current, Set.of()));
			}
		}
		return false;
	}

	private Optional<String> domainOf(String className) {
		Matcher matcher = DOMAIN.matcher(className);
		return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
	}
}
