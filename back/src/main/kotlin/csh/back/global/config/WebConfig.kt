package csh.back.global.config

import csh.back.global.annotation.ApiV1
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.HandlerTypePredicate
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig : WebMvcConfigurer {

    @Value("\${file.upload.dir}")
    private lateinit var uploadDir: String

    override fun configurePathMatch(configurer: PathMatchConfigurer) {
        configurer.addPathPrefix("/api/v1", HandlerTypePredicate.forAnnotation(ApiV1::class.java))
    }

//    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
//        val absolutePath = Paths.get(uploadDir).toAbsolutePath().toUri().toString()
//
//        registry.addResourceHandler("/uploadedimages/**")
//                .addResourceLocations(absolutePath)
//    }
}