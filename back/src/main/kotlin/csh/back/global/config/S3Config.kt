package csh.back.global.config

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Profile("prod")
@Configuration
class S3Config(
    @Value("\${cloud.aws.credentials.access-key}") private val accessKey: String,
    @Value("\${cloud.aws.credentials.secret-key}") private val secretKey: String,
    @Value("\${cloud.aws.region.static}") private val region: String,
    @Value("\${cloud.aws.s3.endpoint}") private val endpoint: String
) {
    @Bean
    fun amazonS3Client(): AmazonS3 {
        val credentials = BasicAWSCredentials(accessKey, secretKey)

        return AmazonS3ClientBuilder.standard()
            .withCredentials(AWSStaticCredentialsProvider(credentials))
            .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(endpoint, region))
            .withPathStyleAccessEnabled(true) // <-- (필수) 오라클 등 타사 스토리지 사용 시 SSL 에러 방지
            .build()
    }
}