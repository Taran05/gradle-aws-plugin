package jp.classmethod.aws.gradle.ec2

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.amazonaws.services.ec2.model.DescribeInstancesResult
import com.amazonaws.services.ec2.model.Instance
import java.util.List;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction;


class AmazonEC2WaitInstanceStatusTask extends DefaultTask {
	
	{
		description 'Wait EC2 instance for specific status.'
		group = 'AWS'
	}
	
	String instanceId
	
	List<String> successStatuses = [
		'running',
		'stopped',
		'terminated'
	]

	List<String> waitStatuses = [
		'pending',
		'shutting-down',
		'stopping'
	]
	
	int loopTimeout = 900 // sec
	int loopWait = 10 // sec
	
	boolean found
	
	String lastStatus
	
	@TaskAction
	def waitStackForStatus() {
		if (! instanceId) throw new GradleException("instanceId is not specified")
		
		AmazonEC2PluginExtension ext = project.extensions.getByType(AmazonEC2PluginExtension)
		AmazonEC2 ec2 = ext.ec2
		
		def start = System.currentTimeMillis()
		while (true) {
			if (System.currentTimeMillis() > start + (loopTimeout * 1000)) {
				throw new GradleException('Timeout')
			}
			try {
				DescribeInstancesResult dir = ec2.describeInstances(new DescribeInstancesRequest()
					.withInstanceIds(instanceId))
				Instance instance = dir.reservations[0].reservation[0]
				if (instance == null) {
					throw new GradleException("${instanceId} is not exists")
				}
				
				found = true
				lastStatus = instance.state.name
				if (successStatuses.contains(lastStatus)) {
					println "Status of ${instanceId} is now ${lastStatus}."
					break
				} else if (waitStatuses.contains(lastStatus)) {
					println "Status of stack ${instanceId} is ${lastStatus}..."
					Thread.sleep(loopWait * 1000)
				} else {
					// waitStatusesでもsuccessStatusesないステータスはfailとする
					throw new GradleException("Status of ${instanceId} is ${lastStatus}.  It seems to be failed.")
				}
			} catch (AmazonServiceException e) {
				if (found) {
					break
				} else {
					throw new GradleException("Fail to describe instance: ${instanceId}", e)
				}
			}
		}
	}
}