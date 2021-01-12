package de.symeda.sormas.backend.systemevent;

import de.symeda.sormas.api.systemevents.SystemEventDto;
import de.symeda.sormas.api.systemevents.SystemEventFacade;
import de.symeda.sormas.api.systemevents.SystemEventStatus;
import de.symeda.sormas.api.systemevents.SystemEventType;
import de.symeda.sormas.api.utils.DateHelper;
import de.symeda.sormas.backend.util.DtoHelper;
import de.symeda.sormas.backend.util.ModelConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.validation.constraints.NotNull;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Stateless(name = "SystemEventFacade")
public class SystemEventFacadeEjb implements SystemEventFacade {

	@PersistenceContext(unitName = ModelConstants.PERSISTENCE_UNIT_NAME)
	private EntityManager em;

	@EJB
	private SystemEventService systemEventService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	public Date getLatestSuccessByType(SystemEventType type) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<SystemEvent> cq = cb.createQuery(SystemEvent.class);
		Root<SystemEvent> systemEvent = cq.from(SystemEvent.class);

		Predicate filter = cb.equal(systemEvent.get(SystemEvent.STATUS), SystemEventStatus.SUCCESS);
		cq.where(filter);
		cq.orderBy(cb.desc(systemEvent.get(SystemEvent.START_DATE)));

		List<SystemEvent> resultList = em.createQuery(cq).getResultList();

		if (resultList != null) {
			return resultList.get(0).getStartDate();
		} else {
			return null;
		}
	}

	@Override
	public void saveSystemEvent(SystemEventDto dto) {
		SystemEvent systemEvent = systemEventService.getByUuid(dto.getUuid());

		systemEvent = fromDto(dto, systemEvent);
		systemEventService.ensurePersisted(systemEvent);

	}

	public SystemEvent fromDto(@NotNull SystemEventDto source, SystemEvent target) {

		if (target == null) {
			target = new SystemEvent();
			target.setUuid(source.getUuid());
		}

		DtoHelper.validateDto(source, target);

		target.setType(source.getType());
		target.setStartDate(source.getStartDate());
		target.setEndDate(source.getEndDate());
		target.setStatus(source.getStatus());
		target.setAdditionalInfo(source.getAdditionalInfo());

		return target;

	}

	@Override
	public void deleteAllDeletableSystemEvents(int daysAfterSystemEventGetsDeleted) {
		deleteAllDeletableSystemEvents(LocalDateTime.now().minusDays(daysAfterSystemEventGetsDeleted));
	}

	public void deleteAllDeletableSystemEvents(LocalDateTime notChangedUntil) {

		long startTime = DateHelper.startTime();

		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<SystemEvent> cq = cb.createQuery(SystemEvent.class);
		Root<SystemEvent> systemEvent = cq.from(SystemEvent.class);

		Timestamp notChangedTimestamp = Timestamp.valueOf(notChangedUntil);
		cq.where(cb.not(systemEventService.createChangeDateFilter(cb, systemEvent, notChangedTimestamp)));

		List<SystemEvent> resultList = em.createQuery(cq).getResultList();
		for (SystemEvent event : resultList) {
			em.remove(event);
		}

		logger.debug(
			"deleteAllDeletableSystemEvents() finished. systemEvent count = {}, {}ms",
			resultList.size(),
			DateHelper.durationMillies(startTime));
	}

	@LocalBean
	@Stateless
	public static class SystemEventFacadeEjbLocal extends SystemEventFacadeEjb {

	}

}
