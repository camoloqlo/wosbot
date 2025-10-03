package cl.camodev.wosbot.almac.repo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import cl.camodev.wosbot.almac.entity.Config;
import cl.camodev.wosbot.almac.entity.Profile;
import cl.camodev.wosbot.almac.jpa.BotPersistence;
import cl.camodev.wosbot.ot.DTOConfig;
import cl.camodev.wosbot.ot.DTOProfiles;

public class ProfileRepository implements IProfileRepository {

	private static ProfileRepository instance;

	private BotPersistence persistence = BotPersistence.getInstance();

	private ProfileRepository() {
	}

	public static ProfileRepository getRepository() {
		if (instance == null) {
			instance = new ProfileRepository();
		}
		return instance;
	}

	@Override
	public List<DTOProfiles> getProfiles() {
		String queryProfiles = "SELECT new cl.camodev.wosbot.ot.DTOProfiles(p.id, p.name, p.emulatorNumber, p.enabled, p.priority, p.reconnectionTime) FROM Profile p";

		// Get profiles using getQueryResults
		List<DTOProfiles> profiles = persistence.getQueryResults(queryProfiles, DTOProfiles.class, null);

		if (profiles == null || profiles.isEmpty()) {
			// Create default profile if none exist
			Profile defaultProfile = new Profile();
			defaultProfile.setName("Default");
			defaultProfile.setEmulatorNumber("0");
			defaultProfile.setEnabled(true);
			defaultProfile.setPriority(50L);
			defaultProfile.setReconnectionTime(0L);

			persistence.createEntity(defaultProfile);

			// Retry getting profiles
			profiles = persistence.getQueryResults(queryProfiles, DTOProfiles.class, null);
		}

		List<Long> profileIds = profiles.stream().map(DTOProfiles::getId).collect(Collectors.toList());

		if (!profileIds.isEmpty()) {
			// Query to get the configurations for the profiles
			String queryConfigs = "SELECT new cl.camodev.wosbot.ot.DTOConfig(c.profile.id, c.key, c.value) " + "FROM Config c WHERE c.profile.id IN :profileIds";

			// Pass parameters to the query
			Map<String, Object> parameters = new HashMap<>();
			parameters.put("profileIds", profileIds);

			// Group configurations by profile ID
			List<DTOConfig> configs = persistence.getQueryResults(queryConfigs, DTOConfig.class, parameters);
			Map<Long, List<DTOConfig>> configMap = configs.stream().collect(Collectors.groupingBy(DTOConfig::getProfileId));

			// Assign configurations to profiles
			profiles.forEach(profile -> profile.setConfigs(configMap.getOrDefault(profile.getId(), new ArrayList<>())));
		}

		return profiles;
	}

	/**
	 * Gets a profile with its configurations by ID.
	 */
	@Override
	public DTOProfiles getProfileWithConfigsById(Long id) {
		if (id == null) {
			return null;
		}

		String queryProfile = "SELECT new cl.camodev.wosbot.ot.DTOProfiles(p.id, p.name, p.emulatorNumber, p.enabled, p.priority, p.reconnectionTime) FROM Profile p WHERE p.id = :id";
		Map<String, Object> params = new HashMap<>();
		params.put("id", id);
		List<DTOProfiles> result = persistence.getQueryResults(queryProfile, DTOProfiles.class, params);
		if (result == null || result.isEmpty()) {
			return null;
		}
		DTOProfiles dto = result.get(0);

		String queryConfigs = "SELECT new cl.camodev.wosbot.ot.DTOConfig(c.profile.id, c.key, c.value) FROM Config c WHERE c.profile.id = :profileId";
		Map<String, Object> paramsCfg = new HashMap<>();
		paramsCfg.put("profileId", id);
		List<DTOConfig> cfgs = persistence.getQueryResults(queryConfigs, DTOConfig.class, paramsCfg);
		dto.setConfigs(cfgs != null ? cfgs : new ArrayList<>());
		return dto;
	}

	@Override
	public boolean addProfile(Profile profile) {
		return persistence.createEntity(profile);
	}

	@Override
	public boolean saveProfile(Profile profile) {
		return persistence.updateEntity(profile);
	}

	@Override
	public boolean deleteProfile(Profile profile) {
		return persistence.deleteEntity(profile);
	}

	@Override
	public Profile getProfileById(Long id) {
		if (id == null) {
			return null;
		}
		return persistence.findEntityById(Profile.class, id);
	}

	@Override
	public List<Config> getProfileConfigs(Long profileId) {
		if (profileId == null) {
			return Collections.emptyList();
		}

		String queryStr = "SELECT c FROM Config c WHERE c.profile.id = :profileId";

		// Crear el mapa de parámetros
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("profileId", profileId);

		// Ejecutar la consulta usando getQueryResults()
		return persistence.getQueryResults(queryStr, Config.class, parameters);
	}

	@Override
	public boolean deleteConfigs(List<Config> configs) {
		if (configs == null || configs.isEmpty()) {
			return false;
		}

		try {
			configs.forEach(config -> persistence.deleteEntity(config));
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public boolean saveConfigs(List<Config> configs) {
		if (configs == null || configs.isEmpty()) {
			return false;
		}

		try {
			configs.forEach(config -> persistence.createEntity(config));
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

}
