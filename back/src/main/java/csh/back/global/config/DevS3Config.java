package csh.back.global.config;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile({"dev", "test"})
@Configuration
public class DevS3Config {

	@Bean
	public AmazonS3 amazonS3Client() {
		BasicAWSCredentials creds = new BasicAWSCredentials("minioadmin", "minioadmin");
		return AmazonS3ClientBuilder.standard()
				.withCredentials(new AWSStaticCredentialsProvider(creds))
				.withEndpointConfiguration(
						new AwsClientBuilder.EndpointConfiguration("http://localhost:9000", "us-east-1"))
				.withPathStyleAccessEnabled(true)
				.build();
	}
}
