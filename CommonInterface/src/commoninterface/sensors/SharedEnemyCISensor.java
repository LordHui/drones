package commoninterface.sensors;

import commoninterface.AquaticDroneCI;
import commoninterface.RobotCI;
import commoninterface.objects.Entity;
import commoninterface.objects.RobotLocation;
import commoninterface.objects.SharedDroneLocation;
import commoninterface.utils.CIArguments;

public class SharedEnemyCISensor extends ConeTypeCISensor{
	
	public SharedEnemyCISensor(int id, RobotCI robot, CIArguments args) {
		super(id, robot, args);
	}

	@Override
	public boolean validEntity(Entity e) {
		if(e instanceof SharedDroneLocation) {
			SharedDroneLocation rl = (SharedDroneLocation)e;
			return rl.getDroneType() == AquaticDroneCI.DroneType.ENEMY;
		}
		return false;
	}
	
}
