package cl.camodev.wosbot.almac.jpa;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.Query;

public final class BotPersistence {

        private static final String PERSISTENCE_UNIT_NAME = "botPU";
        private static final String JDBC_URL_PROPERTY = "jakarta.persistence.jdbc.url";
        private static final String DB_FOLDER = "db";
        private static final String PROFILE_PROPERTY = "bot.profile";
        private static final String SQLITE_PREFIX = "jdbc:sqlite:";
        private static final String PRAGMA_JOURNAL_MODE_WAL = "PRAGMA journal_mode=WAL";
        private static final String PRAGMA_SYNC_NORMAL = "PRAGMA synchronous=NORMAL";
        private static BotPersistence instance;
        private static EntityManagerFactory entityManagerFactory;

        private BotPersistence() {
                try {
                        Map<String, Object> properties = new HashMap<>();
                        properties.put(JDBC_URL_PROPERTY, SQLITE_PREFIX + resolveDatabasePath());
                        entityManagerFactory =
                                        Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME, properties);
                        configureSQLite();
                        PersistenceDataInitialization.initializeData();
                } catch (Exception ex) {
                        System.err.println("Error inicializando EntityManagerFactory: " + ex.getMessage());
                        throw new ExceptionInInitializerError(ex);
                }
        }

        public static BotPersistence getInstance() {
                if (instance == null) {
                        synchronized (BotPersistence.class) {
                                if (instance == null) {
                                        instance = new BotPersistence();
                                }
                        }
                }
                return instance;
        }

        private static String resolveDatabasePath() throws IOException {
                String profile = System.getProperty(PROFILE_PROPERTY, "default");
                Path directory = Paths.get(DB_FOLDER);
                Files.createDirectories(directory);
                return directory.resolve(profile + ".db").toString();
        }

        private void configureSQLite() {
                EntityManager entityManager = getEntityManager();
                try {
                        entityManager.createNativeQuery(PRAGMA_JOURNAL_MODE_WAL).getSingleResult();
                        entityManager.createNativeQuery(PRAGMA_SYNC_NORMAL).executeUpdate();
                } finally {
                        entityManager.close();
                }
        }

	private EntityManager getEntityManager() {
		return entityManagerFactory.createEntityManager();
	}

	public boolean createEntity(Object entity) {
		EntityManager entityManager = getEntityManager();
		try {
			entityManager.getTransaction().begin();
			entityManager.persist(entity);
			entityManager.getTransaction().commit();
			return true;
                } catch (Exception e) {
                        System.err.println("Error creating entity: " + e.getMessage());
                        if (entityManager.getTransaction().isActive()) {
                                entityManager.getTransaction().rollback();
                        }
                        return false;
		} finally {
			entityManager.close(); // Cierra el EntityManager después de cada transacción
		}
	}

	public boolean updateEntity(Object entity) {
		EntityManager entityManager = getEntityManager();
		try {
			entityManager.getTransaction().begin();
			entityManager.merge(entity);
			entityManager.getTransaction().commit();
			return true;
                } catch (Exception e) {
                        System.err.println("Error updating entity: " + e.getMessage());
                        if (entityManager.getTransaction().isActive()) {
                                entityManager.getTransaction().rollback();
                        }
                        return false;
		} finally {
			entityManager.close();
		}
	}

	public boolean deleteEntity(Object entity) {
		EntityManager entityManager = getEntityManager();
		try {
			entityManager.getTransaction().begin();
			entityManager.remove(entityManager.contains(entity) ? entity : entityManager.merge(entity));
			entityManager.getTransaction().commit();
			return true;
                } catch (Exception e) {
                        System.err.println("Error deleting entity: " + e.getMessage());
                        if (entityManager.getTransaction().isActive()) {
                                entityManager.getTransaction().rollback();
                        }
                        return false;
		} finally {
			entityManager.close();
		}
	}

	public <T> T findEntityById(Class<T> entityClass, Object id) {
		EntityManager entityManager = getEntityManager();
		try {
			return entityManager.find(entityClass, id);
		} finally {
			entityManager.close();
		}
	}

	@SuppressWarnings("unchecked")
	public <T> List<T> getQueryResults(String queryString, Class<T> resultClass, Map<String, Object> parameters) {
		EntityManager entityManager = getEntityManager();
		try {
			Query query = entityManager.createQuery(queryString, resultClass);

			// Agregar los parámetros a la Query
			if (parameters != null) {
				for (Map.Entry<String, Object> param : parameters.entrySet()) {
					query.setParameter(param.getKey(), param.getValue());
				}
			}

			return query.getResultList();
		} finally {
			entityManager.close(); // Cerrar el EntityManager después de la ejecución
		}
	}

	public void close() {
		if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
			entityManagerFactory.close();
		}
	}
}
