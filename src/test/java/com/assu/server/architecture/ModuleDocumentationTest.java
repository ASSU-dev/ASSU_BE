package com.assu.server.architecture;

import com.assu.server.ServerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModuleDocumentationTest {

	@Test
	void writeModuleDocumentation() {
		ApplicationModules modules = ApplicationModules.of(ServerApplication.class);

		new Documenter(modules)
			.writeModulesAsPlantUml()
			.writeIndividualModulesAsPlantUml()
			.writeModuleCanvases();
	}
}
