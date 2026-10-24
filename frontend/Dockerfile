FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
ARG VITE_DEMO_ALLOWED_ORIGINS=""
ENV VITE_DEMO_ALLOWED_ORIGINS=$VITE_DEMO_ALLOWED_ORIGINS
RUN npm run build

FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
