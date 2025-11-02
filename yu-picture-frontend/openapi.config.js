
import { generateService } from '@umijs/openapi'

/* 使用@umiJS/openapi 创建前端接口文档 */
generateService ({
  requestLibPath: "import request from '@/request'",
  schemaPath: 'http://localhost:8123/api/v2/api-docs',
  serversPath: './src',
})
