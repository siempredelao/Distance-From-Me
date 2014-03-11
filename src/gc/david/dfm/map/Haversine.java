package gc.david.dfm.map;

import java.text.DecimalFormat;
import java.util.Locale;

/**
 * Haversine class defines static methods to calculate distances between two
 * points on a sphere, in this case, the Earth, from their longitudes and
 * latitudes. It also defines static methods to calculate elevation points.
 * 
 * @author David
 * @see <a href="http://en.wikipedia.org/wiki/Haversine_formula">Haversine</a>
 * 
 */
public final class Haversine {

	/**
	 * Earth radio
	 */
	private static final double RADIUS = 6371000;

	/**
	 * Calculates distance between two positions in meters using JNI.
	 * 
	 * @param lat_a
	 *            Current position latitude in degrees.
	 * @param lon_a
	 *            Current position longitude in degrees.
	 * @param lat_b
	 *            Destination position latitude in degrees.
	 * @param lon_b
	 *            Destination position longitude in degrees.
	 * @return Distance in meters.
	 */
	public static native double getDistanceJNI(double lat_a, double lon_a,
			double lat_b, double lon_b);

	/**
	 * Calculates distance between two positions in meters.
	 * 
	 * @param lat_a
	 *            Current position latitude in degrees.
	 * @param lon_a
	 *            Current position longitude in degrees.
	 * @param lat_b
	 *            Destination position latitude in degrees.
	 * @param lon_b
	 *            Destination position longitude in degrees.
	 * @return Distance in meters.
	 */
	public static double getDistance(double lat_a, double lon_a, double lat_b,
			double lon_b) {
		double lat1 = Math.toRadians(lat_a);
		double lat2 = Math.toRadians(lat_b);
		double lon1 = Math.toRadians(lon_a);
		double lon2 = Math.toRadians(lon_b);

		double dLat = lat2 - lat1;
		double dLon = lon2 - lon1;

		double sinlat = Math.sin(dLat / 2);
		double sinlon = Math.sin(dLon / 2);

		double a = (sinlat * sinlat) + Math.cos(lat1) * Math.cos(lat2)
				* (sinlon * sinlon);
		double c = 2 * Math.asin(Math.min(1.0, Math.sqrt(a)));

		return RADIUS * c;
	}

	/**
	 * Normalizes distance corresponding to its unit and the device locale and
	 * with only two decimal digits. This static function distinguish between
	 * metric and imperial and US customary units.
	 * 
	 * @param distance
	 *            Unformatted distance in metres.
	 * @param locale
	 *            The current locale of the device.
	 * @return A String with the amount and the unit.
	 */
	public static String normalizeDistance(double distance, Locale locale) {

		String resultado, unidadMedida = "km";
		double measure;
		// Uso del formatter para dar con mayor precisi�n que Math.round
		// aunque gaste m�s, pero es una �nica medida la que se normalizar�
		DecimalFormat formatter = new DecimalFormat("##,##0.00");

		if (locale.equals(Locale.CANADA) || locale.equals(Locale.CHINA)
				|| locale.equals(Locale.JAPAN) || locale.equals(Locale.KOREA)
				|| locale.equals(Locale.TAIWAN) || locale.equals(Locale.UK)
				|| locale.equals(Locale.US)) {
			unidadMedida = "mi";

			if (distance >= 1609.344) // Hay al menos una milla
				measure = distance / 1609.344;
			else { // No llega a una milla, medimos en yardas
				unidadMedida = "yd";
				measure = distance * 1.093613298337708;
			}
		} else {
			if (distance >= 1000.0) // Hay al menos un kil�metro
				measure = distance / 1000;
			else { // No llega a un kil�metro, medimos en metros
				unidadMedida = "m";
				measure = distance;
			}
		}
		resultado = formatter.format(measure);
		return resultado + " " + unidadMedida/* + (measure>1 ? "s" : "") */;
	}
	
	/**
	 * Normalizes altitude corresponding to its unit and the device locale and
	 * with only two decimal digits. This static function distinguish between
	 * metric and imperial and US customary units.
	 * 
	 * @param altitude
	 *            Unformatted altitude in metres.
	 * @param locale
	 *            The current locale of the device.
	 * @return A double with only the normalized amount.
	 */
    public static double normalizeAltitude(double altitude, Locale locale){
		double measure;
		
		if (locale.equals(Locale.CANADA)
				|| locale.equals(Locale.UK)
				|| locale.equals(Locale.US))
			measure = altitude/0.3048;
		else
			measure = altitude;
		
		measure = Math.round(measure*1e2)/1e2;
		return measure;
    }
    
	/**
	 * Returns a string with the altitude unit, m or ft, looking to the current locale.
	 * 
	 * @param locale
	 *            Current locale.
	 * @return String with altitude unit.
	 */
    public static String getAltitudeUnit(Locale locale){
    	if (locale.equals(Locale.CANADA)
				|| locale.equals(Locale.UK)
				|| locale.equals(Locale.US))
    		return "ft";
    	else
    		return "m";
    }

	// Load the JNI library
	static {
		System.loadLibrary("gc_david_dfm_map_Haversine");
	}
}
