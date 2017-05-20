package it.md.littlethumb.algorithm.model;

public class Particle {
	
	public double x = 0;
	public double y = 0;
	public double weight = 1.0;

	public Particle() {
	}

	// randomize particle position
	public void randomize(double maxWidth, double maxHeight) {
		this.x = Math.floor(Math.random()*maxWidth);
		this.y = Math.floor(Math.random()*maxHeight);
	}

	// degrade weight: multiply current weight by the given amount
	//	(decimal between 0 and 1)
	public void degrade(double weight) {
		this.weight *= weight;
	}

	// normalizes weight around the maximum such that the particle with
	//	the most weight will now have a weight of 1.0, and all other
	//	particles' weights will scale accordingly
	public boolean normalize(double maxWeight) {
		if(maxWeight == 0) {
			System.out.println("error - maxWeight 0 (check weight function)");
			return false;
		}
		this.weight /= maxWeight;
		return true;
	}

	// returns this particle's weight
	public double getWeight() {
		return this.weight;
	}
	
	// returns a NEW particle object with position and weight identical
	//	to this one
	public Particle clone() {
		Particle copy = new Particle();
		copy.x = this.x;
		copy.y = this.y;
		copy.weight = this.weight;
		return copy;
	}
}
