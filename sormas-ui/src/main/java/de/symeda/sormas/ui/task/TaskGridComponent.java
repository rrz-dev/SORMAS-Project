/*******************************************************************************
 * SORMAS® - Surveillance Outbreak Response Management & Analysis System
 * Copyright © 2016-2018 Helmholtz-Zentrum für Infektionsforschung GmbH (HZI)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/
package de.symeda.sormas.ui.task;

import java.util.ArrayList;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.vaadin.icons.VaadinIcons;
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent;
import com.vaadin.server.FileDownloader;
import com.vaadin.server.FileResource;
import com.vaadin.server.Page;
import com.vaadin.ui.AbstractLayout;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.MenuBar;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;
import com.vaadin.v7.ui.ComboBox;

import de.symeda.sormas.api.EntityRelevanceStatus;
import de.symeda.sormas.api.FacadeProvider;
import de.symeda.sormas.api.i18n.Captions;
import de.symeda.sormas.api.i18n.I18nProperties;
import de.symeda.sormas.api.task.TaskContext;
import de.symeda.sormas.api.task.TaskCriteria;
import de.symeda.sormas.api.task.TaskIndexDto;
import de.symeda.sormas.api.task.TaskStatus;
import de.symeda.sormas.api.task.TaskType;
import de.symeda.sormas.api.user.UserRight;
import de.symeda.sormas.api.user.UserRole;
import de.symeda.sormas.ui.ControllerProvider;
import de.symeda.sormas.ui.UserProvider;
import de.symeda.sormas.ui.ViewModelProviders;
import de.symeda.sormas.ui.utils.ButtonHelper;
import de.symeda.sormas.ui.utils.ComboBoxHelper;
import de.symeda.sormas.ui.utils.CssStyles;
import de.symeda.sormas.ui.utils.LayoutUtil;
import de.symeda.sormas.ui.utils.MenuBarHelper;

import javax.xml.registry.infomodel.User;

@SuppressWarnings("serial")
public class TaskGridComponent extends VerticalLayout {

	private static final String MY_TASKS = "myTasks";
	private static final String OFFICER_TASKS = "officerTasks";

	private TaskCriteria criteria;

	private TaskGrid grid;
	private TasksView tasksView;
	private Map<Button, String> statusButtons;
	private Button activeStatusButton;

	// Filter
	private TaskGridFilterForm filterForm;
	private ComboBox relevanceStatusFilter;

	MenuBar bulkOperationsDropdown;

	private Label viewTitleLabel;
	private String originalViewTitle;

	public TaskGridComponent(Label viewTitleLabel, TasksView tasksView) {
		setSizeFull();
		setMargin(false);

		this.viewTitleLabel = viewTitleLabel;
		this.tasksView = tasksView;
		originalViewTitle = viewTitleLabel.getValue();

		criteria = ViewModelProviders.of(TasksView.class).get(TaskCriteria.class);
		if (criteria.getRelevanceStatus() == null) {
			criteria.relevanceStatus(EntityRelevanceStatus.ACTIVE);
		}

		grid = new TaskGrid(criteria);
		VerticalLayout gridLayout = new VerticalLayout();
		gridLayout.addComponent(createFilterBar());
		gridLayout.addComponent(createAssigneeFilterBar());
		gridLayout.addComponent(grid);
		grid.getDataProvider().addDataProviderListener(e -> updateAssigneeFilterButtons());
		grid.setDataProviderListener(e -> updateAssigneeFilterButtons());

		gridLayout.setMargin(true);
		styleGridLayout(gridLayout);

		addComponent(gridLayout);
	}

	public HorizontalLayout createFilterBar() {
		HorizontalLayout filterLayout = new HorizontalLayout();
		filterLayout.setMargin(false);
		filterLayout.setSpacing(true);
		filterLayout.setSizeUndefined();

		filterForm = new TaskGridFilterForm();
		filterForm.addResetHandler(e -> {
			ViewModelProviders.of(TasksView.class).remove(TaskCriteria.class);
			tasksView.navigateTo(null, true);
		});
		filterForm.addApplyHandler(e -> grid.reload());

		filterLayout.addComponent(filterForm);

		return filterLayout;
	}

	public HorizontalLayout createAssigneeFilterBar() {
		HorizontalLayout assigneeFilterLayout = new HorizontalLayout();
		assigneeFilterLayout.setMargin(false);
		assigneeFilterLayout.setSpacing(true);
		assigneeFilterLayout.setWidth(100, Unit.PERCENTAGE);
		assigneeFilterLayout.addStyleName(CssStyles.VSPACE_3);

		statusButtons = new HashMap<>();

		HorizontalLayout buttonFilterLayout = new HorizontalLayout();
		buttonFilterLayout.setSpacing(true);
		{
			Button allTasks =
				ButtonHelper.createButton(Captions.all, e -> processAssigneeFilterChange(null), ValoTheme.BUTTON_BORDERLESS, CssStyles.BUTTON_FILTER);
			allTasks.setCaptionAsHtml(true);

			buttonFilterLayout.addComponent(allTasks);
			statusButtons.put(allTasks, I18nProperties.getCaption(Captions.all));

			createAndAddStatusButton(Captions.taskOfficerTasks, OFFICER_TASKS, buttonFilterLayout);
			Button myTasks = createAndAddStatusButton(Captions.taskMyTasks, MY_TASKS, buttonFilterLayout);

			Button todaySampleCollectionsBtn = ButtonHelper.createButton(Captions.taskOnlySampleCollectionDueToday, e -> {
				this.filterByTodayDateAndTaskType();
			}, ValoTheme.BUTTON_BORDERLESS, CssStyles.BUTTON_FILTER);
			buttonFilterLayout.addComponent(todaySampleCollectionsBtn);
			statusButtons.put(todaySampleCollectionsBtn, I18nProperties.getCaption(Captions.taskOnlySampleCollectionDueToday));

			Button tomorrowSampleCollectionsBtn = ButtonHelper.createButton(Captions.taskOnlySampleCollectionTomorrow, e -> {
				this.filterByTomorrowDateAndTaskType();
			}, ValoTheme.BUTTON_BORDERLESS, CssStyles.BUTTON_FILTER);
			buttonFilterLayout.addComponent(tomorrowSampleCollectionsBtn);
			statusButtons.put(tomorrowSampleCollectionsBtn, I18nProperties.getCaption(Captions.taskOnlySampleCollectionTomorrow));

			// Default filter for lab users (that don't have any other role) is "My tasks"
			if ((UserProvider.getCurrent().hasUserRole(UserRole.LAB_USER) || UserProvider.getCurrent().hasUserRole(UserRole.EXTERNAL_LAB_USER))
				&& UserProvider.getCurrent().getUserRoles().size() == 1) {
				activeStatusButton = myTasks;
			} else {
				activeStatusButton = allTasks;
			}
		}
		assigneeFilterLayout.addComponent(buttonFilterLayout);

		HorizontalLayout actionButtonsLayout = new HorizontalLayout();
		actionButtonsLayout.setSpacing(true);
		{
			// Show active/archived/all dropdown
			if (UserProvider.getCurrent().hasUserRight(UserRight.TASK_VIEW)) {
				relevanceStatusFilter = ComboBoxHelper.createComboBoxV7();
				relevanceStatusFilter.setId("relevanceStatusFilter");
				relevanceStatusFilter.setWidth(140, Unit.PERCENTAGE);
				relevanceStatusFilter.setNullSelectionAllowed(false);
				relevanceStatusFilter.addItems((Object[]) EntityRelevanceStatus.values());
				relevanceStatusFilter.setItemCaption(EntityRelevanceStatus.ACTIVE, I18nProperties.getCaption(Captions.taskActiveTasks));
				relevanceStatusFilter.setItemCaption(EntityRelevanceStatus.ARCHIVED, I18nProperties.getCaption(Captions.taskArchivedTasks));
				relevanceStatusFilter.setItemCaption(EntityRelevanceStatus.ALL, I18nProperties.getCaption(Captions.taskAllTasks));
				relevanceStatusFilter.addValueChangeListener(e -> {
					criteria.relevanceStatus((EntityRelevanceStatus) e.getProperty().getValue());
					tasksView.navigateTo(criteria);
				});
				actionButtonsLayout.addComponent(relevanceStatusFilter);
			}

			// Bulk operation dropdown
			if (UserProvider.getCurrent().hasUserRight(UserRight.PERFORM_BULK_OPERATIONS)) {
				assigneeFilterLayout.setWidth(100, Unit.PERCENTAGE);
				boolean hasBulkOperationsRight = UserProvider.getCurrent().hasUserRight(UserRight.PERFORM_BULK_OPERATIONS);

				final List<MenuBarHelper.MenuBarItem> menuBarItems = new ArrayList<>();

				menuBarItems.add(
					new MenuBarHelper.MenuBarItem(
						I18nProperties.getCaption(Captions.bulkEdit),
						VaadinIcons.ELLIPSIS_H,
						mi -> ControllerProvider.getTaskController()
							.showBulkTaskDataEditComponent(this.grid.asMultiSelect().getSelectedItems(), () -> tasksView.navigateTo(criteria)),
						hasBulkOperationsRight));
				menuBarItems.add(
					new MenuBarHelper.MenuBarItem(
						I18nProperties.getCaption(Captions.bulkDelete),
						VaadinIcons.TRASH,
						selectedItem -> ControllerProvider.getTaskController()
							.deleteAllSelectedItems(this.grid.asMultiSelect().getSelectedItems(), () -> tasksView.navigateTo(criteria)),
						hasBulkOperationsRight));
				menuBarItems.add(
					new MenuBarHelper.MenuBarItem(
						I18nProperties.getCaption(Captions.actionArchive),
						VaadinIcons.ARCHIVE,
						mi -> ControllerProvider.getTaskController()
							.archiveAllSelectedItems(this.grid.asMultiSelect().getSelectedItems(), () -> tasksView.navigateTo(criteria)),
						hasBulkOperationsRight && EntityRelevanceStatus.ACTIVE.equals(criteria.getRelevanceStatus())));
				menuBarItems.add(
					new MenuBarHelper.MenuBarItem(
						I18nProperties.getCaption(Captions.actionDearchive),
						VaadinIcons.ARCHIVE,
						mi -> ControllerProvider.getTaskController()
							.dearchiveAllSelectedItems(this.grid.asMultiSelect().getSelectedItems(), () -> tasksView.navigateTo(criteria)),
						hasBulkOperationsRight && EntityRelevanceStatus.ARCHIVED.equals(criteria.getRelevanceStatus())));

				bulkOperationsDropdown = MenuBarHelper.createDropDown(Captions.bulkActions, menuBarItems);
				bulkOperationsDropdown = MenuBarHelper.createDropDown(
					Captions.bulkActions,
					new MenuBarHelper.MenuBarItem(I18nProperties.getCaption(Captions.bulkDelete), VaadinIcons.TRASH, selectedItem -> {
						ControllerProvider.getTaskController()
							.deleteAllSelectedItems(grid.asMultiSelect().getSelectedItems(), () -> tasksView.navigateTo(criteria));
					}, UserProvider.getCurrent().hasUserRight(UserRight.TASK_DELETE)),
					new MenuBarHelper.MenuBarItem(I18nProperties.getCaption(Captions.bulkPrint), VaadinIcons.PRINT, c -> {
						this.printSelected(this.grid.asMultiSelect().getSelectedItems(), actionButtonsLayout);
					}, UserProvider.getCurrent().hasUserRight(UserRight.TASK_PRINT_LAB_CERTIFICATE)),
					new MenuBarHelper.MenuBarItem(I18nProperties.getCaption(Captions.bulkMarkAsDone), VaadinIcons.CHECK, c -> {
						ControllerProvider.getTaskController().markAsDone(this.grid.asMultiSelect().getSelectedItems());
						tasksView.navigateTo(criteria);
					}));

				bulkOperationsDropdown.setVisible(tasksView.getViewConfiguration().isInEagerMode());
				actionButtonsLayout.addComponent(bulkOperationsDropdown);
			}
		}
		assigneeFilterLayout.addComponent(actionButtonsLayout);
		assigneeFilterLayout.setComponentAlignment(actionButtonsLayout, Alignment.TOP_RIGHT);
		assigneeFilterLayout.setExpandRatio(actionButtonsLayout, 1);

		return assigneeFilterLayout;
	}


	private void filterByTomorrowDateAndTaskType() {
		criteria.assigneeUser(null);
		criteria.excludeAssigneeUser(null);

		LocalDate localDate = LocalDate.now();
		localDate = localDate.plus(1, ChronoUnit.DAYS);
		LocalDateTime startOfDay = localDate.atStartOfDay();
		criteria.dueDateBetween(Date.from(startOfDay.atZone(ZoneId.systemDefault()).toInstant()),
				Date.from(localDate.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant()));
		criteria.taskType(TaskType.SAMPLE_COLLECTION);
		criteria.taskContext(TaskContext.CASE);
		criteria.taskStatus(TaskStatus.PENDING);

		tasksView.navigateTo(criteria);
	}

	private void filterByTodayDateAndTaskType() {
		criteria.assigneeUser(null);
		criteria.excludeAssigneeUser(null);

		LocalDate localDate = LocalDate.now();
		LocalDateTime startOfDay = localDate.atStartOfDay();
		criteria.dueDateBetween(Date.from(startOfDay.atZone(ZoneId.systemDefault()).toInstant()),
				Date.from(localDate.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant()));
		criteria.taskType(TaskType.SAMPLE_COLLECTION);
		criteria.taskContext(TaskContext.CASE);
		criteria.taskStatus(TaskStatus.PENDING);

		tasksView.navigateTo(criteria);
	}

	private void printSelected(Collection<TaskIndexDto> tasks, AbstractLayout downloadBtnTarget){
		String path = ControllerProvider.getTaskController().printLabornachweise(tasks);

		String rndBtnId = UUID.randomUUID().toString();
		Button downloadInvisibleButton = new Button();
		downloadInvisibleButton.setId(rndBtnId);
		downloadInvisibleButton.addStyleName("invisible");
		downloadBtnTarget.addComponent(downloadInvisibleButton);
		FileDownloader fileDownloader = new FileDownloader(new FileResource(new File(path)));
		fileDownloader.extend(downloadInvisibleButton);

		//The FileDownloader can not extend the "Download" menu item
		//That's why we are creating an invisible button and simulate the click event.
		Page.getCurrent().getJavaScript().execute("document.getElementById('" + rndBtnId + "').click();");
		removeComponent(downloadInvisibleButton);
	}

	private void styleGridLayout(VerticalLayout gridLayout) {
		gridLayout.setSpacing(false);
		gridLayout.setSizeFull();
		gridLayout.setExpandRatio(grid, 1);
		gridLayout.setStyleName("crud-main-layout");
	}

	private void processAssigneeFilterChange(String assignee) {
		if (OFFICER_TASKS.equals(assignee)) {
			criteria.assigneeUser(null);
			criteria.excludeAssigneeUser(FacadeProvider.getUserFacade().getCurrentUserAsReference());
		} else if (MY_TASKS.equals(assignee)) {
			criteria.excludeAssigneeUser(null);
			criteria.assigneeUser(FacadeProvider.getUserFacade().getCurrentUserAsReference());
		} else {
			criteria.excludeAssigneeUser(null);
			criteria.assigneeUser(null);
		}

		tasksView.navigateTo(criteria);
	}

	private Button createAndAddStatusButton(String captionKey, String status, HorizontalLayout filterLayout) {
		Button button = ButtonHelper.createButton(
			captionKey,
			e -> processAssigneeFilterChange(status),
			ValoTheme.BUTTON_BORDERLESS,
			CssStyles.BUTTON_FILTER,
			CssStyles.BUTTON_FILTER_LIGHT);
		button.setData(status);
		button.setCaptionAsHtml(true);

		filterLayout.addComponent(button);
		statusButtons.put(button, button.getCaption());

		return button;
	}

	public void reload(ViewChangeEvent event) {
		String params = event.getParameters().trim();
		if (params.startsWith("?")) {
			params = params.substring(1);
			criteria.fromUrlParams(params);
		}
		updateFilterComponents();
		grid.reload();
	}

	private void updateAssigneeFilterButtons() {
		statusButtons.keySet().forEach(b -> {
			CssStyles.style(b, CssStyles.BUTTON_FILTER_LIGHT);
			b.setCaption(statusButtons.get(b));
			if ((OFFICER_TASKS.equals(b.getData()) && criteria.getExcludeAssigneeUser() != null)
				|| (MY_TASKS.equals(b.getData()) && criteria.getAssigneeUser() != null)) {
				activeStatusButton = b;
			}
		});
		if (activeStatusButton != null) {
			CssStyles.removeStyles(activeStatusButton, CssStyles.BUTTON_FILTER_LIGHT);
			activeStatusButton
				.setCaption(statusButtons.get(activeStatusButton) + LayoutUtil.spanCss(CssStyles.BADGE, String.valueOf(grid.getItemCount())));
		}
	}

	public void updateFilterComponents() {
		// TODO replace with Vaadin 8 databinding
		tasksView.setApplyingCriteria(true);

		updateAssigneeFilterButtons();
		if (relevanceStatusFilter != null) {
			relevanceStatusFilter.setValue(criteria.getRelevanceStatus());
		}

		filterForm.setValue(criteria);

		tasksView.setApplyingCriteria(false);
	}

	public TaskGrid getGrid() {
		return grid;
	}

	public MenuBar getBulkOperationsDropdown() {
		return bulkOperationsDropdown;
	}

	public TaskCriteria getCriteria() {
		return criteria;
	}
}
