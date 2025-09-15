package cl.camodev.wosbot.serv.impl;

import java.util.HashMap;
import java.util.List;

import cl.camodev.wosbot.almac.entity.Config;
import cl.camodev.wosbot.almac.repo.ConfigRepository;
import cl.camodev.wosbot.almac.repo.IConfigRepository;

public class ServConfig {

	private static ServConfig instance;

	private IConfigRepository iConfigRepository = ConfigRepository.getRepository();

	private ServConfig() {

	}

	public static ServConfig getServices() {
		if (instance == null) {
			instance = new ServConfig();
		}
		return instance;
	}

	public HashMap<String, String> getGlobalConfig() {
		List<Config> configs = iConfigRepository.getGlobalConfigs();

		if (configs == null || configs.isEmpty()) {
			return null;
		}

		HashMap<String, String> globalConfig = new HashMap<>();
		for (Config config : configs) {
			globalConfig.put(config.getKey(), config.getValue());
		}
		return globalConfig;
	}

}
