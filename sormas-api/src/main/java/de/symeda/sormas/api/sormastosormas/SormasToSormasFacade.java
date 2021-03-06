/*
 * SORMAS® - Surveillance Outbreak Response Management & Analysis System
 * Copyright © 2016-2020 Helmholtz-Zentrum für Infektionsforschung GmbH (HZI)
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package de.symeda.sormas.api.sormastosormas;

import java.util.List;

import javax.ejb.Remote;

import de.symeda.sormas.api.sormastosormas.sharerequest.ShareRequestDataType;

@Remote
public interface SormasToSormasFacade {

	List<ServerAccessDataReferenceDto> getAvailableOrganizations();

	ServerAccessDataReferenceDto getOrganizationRef(String id);

	List<SormasToSormasShareInfoDto> getShareInfoIndexList(SormasToSormasShareInfoCriteria criteria, Integer first, Integer max);

	void rejectShareRequest(ShareRequestDataType dataType, String uuid) throws SormasToSormasException;

	void acceptShareRequest(ShareRequestDataType dataType, String uuid) throws SormasToSormasException, SormasToSormasValidationException;

	void revokeShare(String shareInfoUuid) throws SormasToSormasException;

	void revokeRequests(SormasToSormasEncryptedDataDto encryptedRequestUuid) throws SormasToSormasException;

	boolean isFeatureEnabled();
}
