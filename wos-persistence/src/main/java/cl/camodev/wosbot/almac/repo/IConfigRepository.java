
package cl.camodev.wosbot.almac.repo;

import java.util.List;

import cl.camodev.wosbot.almac.entity.Config;
import cl.camodev.wosbot.almac.entity.TpConfig;
import cl.camodev.wosbot.console.enumerable.TpConfigEnum;

public interface IConfigRepository {

	boolean addConfig(Config config);

	boolean saveConfig(Config config);

	boolean deleteConfig(Config config);

	Config getConfigById(Long id);

	List<Config> getProfileConfigs(Long profileId);

	List<Config> getGlobalConfigs();

	TpConfig getTpConfig(TpConfigEnum tpConfig);
}
