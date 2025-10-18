package cl.camodev.wosbot.serv.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import cl.camodev.wosbot.almac.entity.Config;
import cl.camodev.wosbot.almac.entity.Profile;
import cl.camodev.wosbot.almac.entity.TpConfig;
import cl.camodev.wosbot.almac.repo.ConfigRepository;
import cl.camodev.wosbot.almac.repo.IConfigRepository;
import cl.camodev.wosbot.almac.repo.IProfileRepository;
import cl.camodev.wosbot.almac.repo.ProfileRepository;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.TpConfigEnum;
import cl.camodev.wosbot.ot.DTOProfileStatus;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.IProfileDataChangeListener;
import cl.camodev.wosbot.serv.IProfileStatusChangeListener;
import cl.camodev.wosbot.serv.IServProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServProfiles implements IServProfile {

	private final static Logger logger = LoggerFactory.getLogger(ServProfiles.class);

	private static ServProfiles instance;


	private final IProfileRepository iProfileRepository;

	private final IConfigRepository iConfigRepository;

	private List<IProfileStatusChangeListener> listeners;

	private List<IProfileDataChangeListener> dataChangeListeners;

	private ServProfiles() {
		iProfileRepository = ProfileRepository.getRepository();
		iConfigRepository = ConfigRepository.getRepository();
	}

	public static ServProfiles getServices() {
		if (instance == null) {
			instance = new ServProfiles();
		}
		return instance;
	}

	@Override
	public List<DTOProfiles> getProfiles() {
		return iProfileRepository.getProfiles();
	}

	public HashMap<EnumConfigurationKey, String> getGlobalSettings() {
		List<Config> configs = iConfigRepository.getGlobalConfigs();
		if (configs != null) {
			HashMap<EnumConfigurationKey, String> settings = new HashMap<EnumConfigurationKey, String>();
			for (Config config : configs) {
				settings.put(EnumConfigurationKey.valueOf(config.getKey()), config.getValue());
			}
			return settings;
		} else {
			return null;
		}

	}

	@Override
	public boolean addProfile(DTOProfiles profile) {
		try {
			if (profile == null) {
				return false;
			}

			Profile newProfile = new Profile();
			newProfile.setName(profile.getName());
			newProfile.setEmulatorNumber(profile.getEmulatorNumber());
			newProfile.setEnabled(profile.getEnabled());
			newProfile.setPriority(profile.getPriority());
			newProfile.setReconnectionTime(profile.getReconnectionTime());

			boolean success = iProfileRepository.addProfile(newProfile);
			if (success) {
				notifyProfileDataChange(null);
			}
			return success;

		} catch (Exception e) {
			logger.error("Error occurred while adding profile: {}", e.getMessage());
			return false;
		}
	}

	@Override
	public boolean saveProfile(DTOProfiles profileDTO) {
		try {
			if (profileDTO == null || profileDTO.getId() == null) {
				return false;
			}

			// Get the existing profile
			Profile existingProfile = iProfileRepository.getProfileById(profileDTO.getId());

			if (existingProfile == null) {
				return false;
			}

			// Update the profile fields
			existingProfile.setName(profileDTO.getName());
			existingProfile.setEmulatorNumber(profileDTO.getEmulatorNumber());
			existingProfile.setEnabled(profileDTO.getEnabled());
			existingProfile.setPriority(profileDTO.getPriority());
			existingProfile.setReconnectionTime(profileDTO.getReconnectionTime());

			List<Config> existingConfigs = iConfigRepository.getProfileConfigs(existingProfile.getId());
			for (Config config : existingConfigs) {
				iConfigRepository.deleteConfig(config);
			}

			TpConfig tpConfig = iConfigRepository.getTpConfig(TpConfigEnum.PROFILE_CONFIG);

			if (tpConfig == null) {
				return false;
			}

			List<Config> newConfigs = profileDTO.getConfigs().stream().map(dtoConfig -> new Config(existingProfile, tpConfig, dtoConfig.getConfigurationName(), dtoConfig.getValue())).collect(Collectors.toList());

			newConfigs.forEach(config -> iConfigRepository.addConfig(config));

			boolean success = iProfileRepository.saveProfile(existingProfile);
			if (success) {
				notifyProfileDataChange(profileDTO);
			}
			return success;

		} catch (Exception e) {
			logger.error("Error occurred while saving profile: {}", e.getMessage());
			return false;
		}
	}

	@Override
	public boolean deleteProfile(DTOProfiles profile) {
		try {
			if (profile == null || profile.getId() == null) {
				return false;
			}

			Profile existingProfile = iProfileRepository.getProfileById(profile.getId());

			if (existingProfile == null) {
				return false;
			}

			List<Config> existingConfigs = iConfigRepository.getProfileConfigs(existingProfile.getId());
			for (Config config : existingConfigs) {
				iConfigRepository.deleteConfig(config);
			}

			boolean success = iProfileRepository.deleteProfile(existingProfile);
			if (success) {
				notifyProfileDataChange(profile);
			}
			return success;

		} catch (Exception e) {
			logger.error("Error occurred while deleting profile: {}", e.getMessage());
			return false;
		}
	}

	@Override
	public boolean bulkUpdateProfiles(DTOProfiles templateProfile) {
		try {
			if (templateProfile == null) {
				return false;
			}

			// Get all profiles
			List<DTOProfiles> allProfiles = getProfiles();
			
			if (allProfiles == null || allProfiles.isEmpty()) {
				return false;
			}

			boolean allSuccessful = true;

			for (DTOProfiles profile : allProfiles) {
				try {
					// Copy all configurations from template profile
					profile.getConfigs().clear();
					profile.getConfigs().addAll(templateProfile.getConfigs());
					
					// Save the updated profile
					boolean saved = saveProfile(profile);
					if (!saved) {
						allSuccessful = false;
						logger.warn("Failed to save profile: {}", profile.getName());
					}
				} catch (Exception e) {
				logger.error("Error occurred while updating profile {}: {}", profile.getName(), e.getMessage());
					allSuccessful = false;
				}
			}

			return allSuccessful;

		} catch (Exception e) {
			logger.error("Error occurred while bulk updating profiles: {}", e.getMessage());
			return false;
		}
	}

	public void notifyProfileStatusChange(DTOProfileStatus statusDto) {
		if (listeners != null) {
			listeners.forEach(listener -> {
				listener.onProfileStatusChange(statusDto);
			});
		}
	}

	@Override
	public void addProfileStatusChangeListerner(IProfileStatusChangeListener listener) {
		if (listeners == null) {
			listeners = new ArrayList<IProfileStatusChangeListener>();
		}
		listeners.add(listener);
	}

	@Override
	public void addProfileDataChangeListener(IProfileDataChangeListener listener) {
		if (dataChangeListeners == null) {
			dataChangeListeners = new ArrayList<>();
		}
		dataChangeListeners.add(listener);
	}

	public void notifyProfileDataChange(DTOProfiles profile) {
		if (dataChangeListeners != null) {
			for (IProfileDataChangeListener listener : dataChangeListeners) {
				listener.onProfileDataChanged(profile);
			}
		}
	}
}
