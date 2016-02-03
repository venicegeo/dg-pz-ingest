package ingest.inspect;

import model.data.DataResource;

/**
 * Inspects a Shapefile.
 * 
 * @author Patrick.Doody
 * 
 */
public class ShapefileInspector implements InspectorType {

	@Override
	public DataResource inspect(DataResource dataResource) {
		return dataResource;
	}
}
