package commoninterface.neuralnetwork.inputs;

import commoninterface.AquaticDroneCI;
import commoninterface.CISensor;
import commoninterface.sensors.CompassCISensor;

public class CompassCINNInput extends CINNInput {

	public CompassCINNInput(CISensor sensor) {
		super(sensor);
	}
	
	@Override
	public int getNumberOfInputValues() {
		return 1;
	}

	@Override
	public double getValue(int index) {
		return ((CompassCISensor)cisensor).getSensorReading(index);
	}

}
