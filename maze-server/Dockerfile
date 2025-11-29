# Dockerfile to build and run BOLD
#
# sudo docker build . -t bold-server
# sudo docker run -p 8080:8080 -it bold-server
# sudo docker run -p 8080:8080 -e TASKNAME=ts1 -it bold-server

# Build BOLD
FROM gradle:8.5.0-jdk21 AS build

RUN mkdir /opt/bold-build

COPY ./ /opt/bold-build/

WORKDIR /opt/bold-build

RUN gradle install


# Run BOLD
FROM eclipse-temurin:21

ENV TASKNAME=""
ENV BOLD_SERVER_BASE_URI="http://127.0.1.1:8080/"

COPY --from=0 /opt/bold-build/build /opt/bold

WORKDIR /opt/bold/install/bold-server

CMD ["sh", "-lc", "bin/bold-server ${TASKNAME}"]

