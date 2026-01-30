# Dockerfile to build and run MASE
#
# docker build . -t mase-server
# docker run -p 8080:8080 -it mase-server
# docker run -p 8080:8080 -e TASKNAME=ts1 -it mase-server

# Build MASE
FROM gradle:8.5.0-jdk21 AS build

RUN mkdir /opt/mase-build

COPY ./ /opt/mase-build/

WORKDIR /opt/mase-build

RUN gradle install


# Run MASE
FROM eclipse-temurin:21

ENV TASKNAME=""
ENV MASE_SERVER_BASE_URI="http://127.0.1.1:8080/"

COPY --from=0 /opt/mase-build/build /opt/mase

WORKDIR /opt/mase/install/mase-server

CMD ["sh", "-lc", "bin/mase-server ${TASKNAME}"]

