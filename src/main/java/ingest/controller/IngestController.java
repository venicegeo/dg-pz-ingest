/**
 * Copyright 2016, RadiantBlue Technologies, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package ingest.controller;

import ingest.messaging.IngestThreadManager;
import ingest.persist.PersistMetadata;
import ingest.utility.IngestUtilities;

import java.util.HashMap;
import java.util.Map;

import model.data.DataResource;
import model.job.metadata.ResourceMetadata;
import model.response.ErrorResponse;
import model.response.PiazzaResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import util.PiazzaLogger;

/**
 * REST Controller for ingest. Ingest has no functional REST endpoints, as all
 * communication is done through Kafka. However, this controller exposes useful
 * debug/status endpoints which can be used administratively.
 * 
 * @author Patrick.Doody
 * 
 */
@RestController
public class IngestController {
	@Autowired
	private IngestThreadManager threadManager;
	@Autowired
	private PiazzaLogger logger;
	@Autowired
	private PersistMetadata persistence;
	@Autowired
	private IngestUtilities ingestUtil;

	/**
	 * Deletes the Data resource object from the Resources collection.
	 * 
	 * @param dataId
	 *            ID of the Resource
	 * @return The resource matching the specified ID
	 */
	@RequestMapping(value = "/data/{dataId}", method = RequestMethod.DELETE)
	public PiazzaResponse deleteData(@PathVariable(value = "dataId") String dataId) {
		try {
			if (dataId.isEmpty()) {
				throw new Exception("No Data ID specified.");
			}
			// Query for the Data ID
			DataResource data = persistence.getData(dataId);
			if (data == null) {
				logger.log(String.format("Data not found for requested ID %s", dataId), PiazzaLogger.WARNING);
				return new ErrorResponse(null, String.format("Data not found: %s", dataId), "Loader");
			}
			// Delete the Data if hosted
			ingestUtil.deleteDataResourceFiles(data);
			// Remove the Data from the database
			persistence.deleteDataEntry(dataId);
			return null;
		} catch (Exception exception) {
			exception.printStackTrace();
			logger.log(String.format("Error deleting Data %s: %s", dataId, exception.getMessage()), PiazzaLogger.ERROR);
			return new ErrorResponse(null, "Error deleting Data: " + exception.getMessage(), "Loader");
		}
	}

	/**
	 * Update the metadata of a Data Resource
	 * 
	 * @param dataId
	 *            The ID of the resource
	 * @param user
	 *            the user submitting the request
	 * @return OK if successful; error if not.
	 */
	@RequestMapping(value = "/data/{dataId}", method = RequestMethod.POST)
	public PiazzaResponse updateMetadata(@PathVariable(value = "dataId") String dataId,
			@RequestBody ResourceMetadata metadata) {
		try {
			// Update the Metadata
			persistence.updateMetadata(dataId, metadata);
			// Return OK
			return null;
		} catch (Exception exception) {
			String error = String.format("Could not update Metadata %s", exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ErrorResponse(null, error, "Access");
		}
	}

	/**
	 * Returns administrative statistics for this component.
	 * 
	 * @return Component information
	 */
	@RequestMapping(value = "/admin/stats", method = RequestMethod.GET)
	public ResponseEntity<Map<String, Object>> getAdminStats() {
		Map<String, Object> stats = new HashMap<String, Object>();
		// Return information on the jobs currently being processed
		stats.put("jobs", threadManager.getRunningJobIDs());
		return new ResponseEntity<Map<String, Object>>(stats, HttpStatus.OK);
	}

	/**
	 * Healthcheck required for all Piazza Core Services
	 * 
	 * @return String
	 */
	@RequestMapping(value = "/", method = RequestMethod.GET)
	public String getHealthCheck() {
		return "Hello, Health Check here for Loader.";
	}
}
