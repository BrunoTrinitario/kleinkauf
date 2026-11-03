# syntax=docker/dockerfile:1
#Imagen de produccion del frontend: compila con Vite y sirve los estaticos con nginx.
#Las variables VITE_* quedan fijas en el build (Vite las reemplaza al compilar), por eso son build args
FROM node:22.11.0-alpine3.20 AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
#URL publica del gateway y host de la plataforma (sin tienda). Pisan lo que diga .env
ARG VITE_API_BASE_PUBLIC=http://localhost:3000
ARG VITE_PLATFORM_HOSTNAME=localhost
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
