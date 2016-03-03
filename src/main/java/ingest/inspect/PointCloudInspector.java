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
package ingest.inspect;

import java.io.IOException;
import model.data.DataResource;
import model.data.location.FileAccessFactory;
import model.data.location.FileLocation;
import model.data.response.PointCloudResponse;
import model.data.type.PointCloudResource;
import model.job.metadata.SpatialMetadata;
import org.apache.commons.io.IOUtils;
import org.geotools.referencing.CRS;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Inspects Point Cloud response file, parsing essential metadata from json.
 * 
 * @author Sonny.Saniev
 * 
 */
@Component
public class PointCloudInspector implements InspectorType {
	@Value("${s3.key.access:}")
	private String AMAZONS3_ACCESS_KEY;
	@Value("${s3.key.private:}")
	private String AMAZONS3_PRIVATE_KEY;

	private static final String POINT_CLOUD_ENDPOINT = "http://pzsvc-pdal.cf.piazzageo.io/api/v1/pdal";
	
	@Override
	public DataResource inspect(DataResource dataResource, boolean host) throws Exception {

		// Load point cloud post request template
		ClassLoader classLoader = getClass().getClassLoader();
		String pointCloudTemplate = IOUtils.toString(classLoader.getResourceAsStream("templates/pointCloudRequest.json"));
		
		// Obtain File URL from AWS S3 Bucket
		FileAccessFactory fileFactory = new FileAccessFactory(AMAZONS3_ACCESS_KEY, AMAZONS3_PRIVATE_KEY);
		FileLocation fileLocation = ((PointCloudResource) dataResource.getDataType()).getLocation();
		String awsS3Url = fileFactory.getFileUri(fileLocation);
		//String awsS3Url = "https://s3.amazonaws.com/venicegeo-sample-data/pointcloud/samp71-utm.laz"; // sample working file

		// Inject URL into the Post Payload
		String payloadBody = String.format(pointCloudTemplate, awsS3Url);
		
		// Post payload to point cloud endpoint for the response payload
		PointCloudResponse pointCloudResponse = postPointCloudTemplate(POINT_CLOUD_ENDPOINT, payloadBody);

		// Set the Metadata
		SpatialMetadata spatialMetadata = new SpatialMetadata();
		spatialMetadata.setMaxX(pointCloudResponse.getMaxx());
		spatialMetadata.setMaxY(pointCloudResponse.getMaxy());
		spatialMetadata.setMaxZ(pointCloudResponse.getMaxz());
		spatialMetadata.setMinX(pointCloudResponse.getMinx());
		spatialMetadata.setMinY(pointCloudResponse.getMiny());
		spatialMetadata.setMinZ(pointCloudResponse.getMinz());
		spatialMetadata.setCoordinateReferenceSystem(pointCloudResponse.getSpatialreference());
		
		// Pull EPSG code from decoded CoordinateReferenceSystem, CRS.decode breaks
		// Remove \ escape character from spatial reference string, replace \" with "
//		String removeEscapeCharacters = pointCloudResponse.getSpatialreference().replace("\\\"", "\"");
//	    CoordinateReferenceSystem coordinateReferenceSystem = CRS.parseWKT(removeEscapeCharacters);
//	    spatialMetadata.setEpsgCode(CRS.lookupEpsgCode(coordinateReferenceSystem, true));
		

		// Set the DataResource Spatial Metadata
		dataResource.spatialMetadata = spatialMetadata;

		return dataResource;
	}

	/**
	 * Executes POST request to Point Cloud to grab the Payload
	 * 
	 * @param url
	 *            The URL to post for point cloud api
	 * @return The PointCloudResponse object containing metadata.
	 * 
	 * @throws IOException 
	 * @throws JsonProcessingException 
	 */
	private PointCloudResponse postPointCloudTemplate(String url, String payload) throws JsonProcessingException, IOException {
		// Setup Basic Headers
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		// Create the Request template and execute post
		HttpEntity<String> request = new HttpEntity<String>(payload, headers);
		RestTemplate restTemplate = new RestTemplate();
		String response = restTemplate.postForObject(url, request, String.class);
		
		// Parse required fields from point cloud json response
		ObjectMapper mapper = new ObjectMapper();
		JsonNode root = mapper.readTree(response);
		double maxx = root.at("/response/metadata/maxx").asDouble();
		double maxy = root.at("/response/metadata/maxy").asDouble();
		double maxz = root.at("/response/metadata/maxz").asDouble();
		double minx = root.at("/response/metadata/minx").asDouble();
		double miny = root.at("/response/metadata/miny").asDouble();
		double minz = root.at("/response/metadata/minz").asDouble();
		String spatialreference = root.at("/response/metadata/spatialreference").asText();
		
		// Return the new PointCloudResponse object
		return new PointCloudResponse(spatialreference, maxx, maxy, maxz, minx, miny, minz);
	}
}
