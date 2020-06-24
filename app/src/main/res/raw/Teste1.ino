#include <ESP8266WiFi.h>        // Include the Wi-Fi library
#include <EEPROM.h>

#define BUILT_IN_LED 2


char strMac[30];


WiFiServer wifiServer(80);



#define		FIXED_SSID_ACCESS_POINT		"8266_THERMOMETER"
#define		FIXED_PASSWD_ACCESS_POINT	"nana12345"

#define	ACCESS_POINT	'A'
#define	CLIENT			'C'


// ===== Configuração ===============
#define	MAX_LEN		30

char strSSID[MAX_LEN + 1];
char strPasswd[MAX_LEN + 1];
long timeoutConexaoAsClient      = 20; // segundos
long timeoutConexaoAsAccessPoint = 60 * 10; // segundos

#define	PARAM_CONFIG_SSID				1
#define	PARAM_CONFIG_PASSWD				2
#define	PARAM_TIMEOUT_AS_CLIENT 		3
#define	PARAM_TIMEOUT_AS_ACCESS_POINT 	4

void gravaConfigEEPROM(void);
void recuperaConfigEEPROM(void);
void salvaConfigEEPROM(void);

int operationMode;
char *strErroConexao="";

void setup() {
	char c1, c2;

	pinMode(BUILT_IN_LED, OUTPUT);
	
	Serial.begin(115200);

	recuperaConfigEEPROM();

	if ( startConexao() > 0 ) {
		operationMode=CLIENT;
		wifiServer.begin();
	} else {
		operationMode=ACCESS_POINT;
		wifiServer.begin();
	}

}

void loop()
{
	if ( operationMode == CLIENT ) {
		processaModoClient();
	} else {
		processaModoAccessPoint();
	}
    Serial.printf("Stations connected = %d\n", WiFi.softAPgetStationNum());

    	long xxx=0;
	time(&xxx);
	char aux[100];
	sprintf(aux, "Time: %ld", xxx);
    Serial.println(aux);

  delay(3000);
}

void processaModoClient(void)
{	static int viuAlguem=0;
}

void processaModoAccessPoint(void)
{	static int viuAlguem=0;
	int i;
	long now;
	long entradaEmModoAccessPoint;
	char linhaRecebida[100];
	int indRx;
	int conta = 5;

	time(&entradaEmModoAccessPoint);

	while ( 1 ) {
		if ( (time(NULL) - entradaEmModoAccessPoint) > timeoutConexaoAsAccessPoint  ) {
			hardwareReset("Timeout para operar como AccessPoint");
		}


		if ( WiFi.softAPgetStationNum() == 0 ) {
			if ( --conta == 0 ) {
				// Pisca led
				digitalWrite(BUILT_IN_LED, LOW);  delay(100); digitalWrite(BUILT_IN_LED, HIGH); delay( 100); digitalWrite(BUILT_IN_LED, LOW);  delay(100); digitalWrite(BUILT_IN_LED, HIGH);
				delay(500); 
				digitalWrite(BUILT_IN_LED, LOW);  delay(100); digitalWrite(BUILT_IN_LED, HIGH); delay( 100); digitalWrite(BUILT_IN_LED, LOW);  delay(100); digitalWrite(BUILT_IN_LED, HIGH);
				delay(500); 
				conta = 5;
				Serial.print("softAPgetStationNum : "); Serial.println(WiFi.softAPgetStationNum(), DEC); 
			} else {
				delay(1000);
			}
			continue;
		}


		Serial.print("softAPgetStationNum"); Serial.println(WiFi.softAPgetStationNum(), DEC); 

		WiFiClient client = wifiServer.available();

		Serial.print("Aguardando cliente "); Serial.println((timeoutConexaoAsAccessPoint - (time(NULL) - entradaEmModoAccessPoint)), DEC); 
		Serial.println(client, DEC); 

		
		if ( client ) {
			indRx = 0;
			
	    	while ( client.connected() ) {

				if ( (time(NULL) - entradaEmModoAccessPoint) > timeoutConexaoAsAccessPoint  ) {
					hardwareReset("Timeout para operar como AccessPoint");
				}
	 
		      	while (client.available() > 0) {
		        	char c = client.read();
		        	
		        	if ( indRx >= sizeof(linhaRecebida) ) {
		        		indRx = 0;
		        		continue;
		        	}

					// Processa linha recebida
		        	if ( (c == '\n') || (c == '\r')  ) {
		        		client.write("OK\n\r");
		        		linhaRecebida[indRx] = '\0';
		        		if ( linhaRecebida[0] == '[' ) {
			        		if ( trataLinhaConfiguracao(linhaRecebida) > 0 ) {
			        			// reseta processador
								hardwareReset("Nova configuração recebida");
			        		} else {
			        			indRx = 0;
	        			 		Serial.print("Linha Rejeitada : ");
	        			 		Serial.println(linhaRecebida);
			        		}
			        	} else {
			        		// Linhas pequenas ou invalidas são descartadas
		        			indRx = 0;
        			 		Serial.print("Linha Descartada : ");
        			 		Serial.println(linhaRecebida);
			        	}
		        	} else {
		        		// armazena caracter recebido na linha a ser processada
			        	linhaRecebida[indRx++] = c;
			        	linhaRecebida[indRx] = '\0';
		        	}
		      	}
		 
		      	delay(10);
	    	}
	    	
		    client.stop();
		    Serial.println("Client disconnected");
		} else {
			static int pisca;
			pisca++;
			digitalWrite(BUILT_IN_LED, (pisca & 0x1));  
			delay(1000); 
		}
	}
}

//---------------------------------------------------------
// trataLinhaConfiguracao
//---------------------------------------------------------
int trataLinhaConfiguracao(char *linha)
{	int tam = strlen(linha);
	char *p1=NULL;
	char *p2=NULL;
	char *p3=NULL;
	char *p4=NULL;
	char *start;
	char *p;
	int indConfig=0;
	int fim = 0;
	int indLinha=1; // pula '[';

	if ( linha[0] != '[') return(0); 
	if ( tam < 10 ) return(0); 
	if ( linha[tam -1] != ']') return(0); 
	
	p = start = &linha[1];
	tam = 0;
	fim = 0;
	while ( ! fim ) {

		// Anda até achar um TAB ou fim da linha
		if ( (*p != '\t') && ( *p != '\0' ) ) {
			if ( tam++ >= MAX_LEN ) {
				return(0);
			}
			p++;
			continue;
		}
		
		*p++ = '\0';
		
		// Processa parametro
		switch(++indConfig) {
			case PARAM_CONFIG_SSID  			: strcpy(strSSID, start); break;
			case PARAM_CONFIG_PASSWD  			: strcpy(strPasswd, start);  break;
			case PARAM_TIMEOUT_AS_CLIENT  		: timeoutConexaoAsClient = atoi(start); break;
			case PARAM_TIMEOUT_AS_ACCESS_POINT  : timeoutConexaoAsAccessPoint = atoi(start); break;
			default: 
				fim = 1;
				break;
		}
		start = p;
	}
	
	if ( (timeoutConexaoAsClient >= 0) || (timeoutConexaoAsClient > 60) ) {
		Serial.println("timeoutConexaoAsClient Invalido");
		return(0);
	}

	if ( (timeoutConexaoAsAccessPoint >= 0) || (timeoutConexaoAsAccessPoint > 60) ) {
		Serial.println("timeoutConexaoAsClient Invalido");
		return(0);
	}

	Serial.print("strSSID                     : "); Serial.println(strSSID);
	Serial.print("strPasswd                   : "); Serial.println(strPasswd);
	Serial.print("timeoutConexaoAsClient      : "); Serial.println( timeoutConexaoAsClient, DEC );
	Serial.print("timeoutConexaoAsAccessPoint : "); Serial.println( timeoutConexaoAsAccessPoint, DEC );

	salvaConfigEEPROM();
	return(1);
}

//---------------------------------------------------------
// trataConexaoAsClient
//---------------------------------------------------------
int startConexao(void)
{	int fim = 0;
	long start=0;
	long now=0;

	time(&start);

	if  ( strSSID[0] == '\0' ) {
		strErroConexao = "Não configurado";
	} else {
				
		// Inicialmente vamos tentar conectar na rede configurada
		WiFi.begin(strSSID, strPasswd); 

		fim = 0;
		while( ! fim  ) {
			delay(200);
			switch( WiFi.status() ) {
				case WL_CONNECTED :
					fim = 1;
					break;
				case WL_NO_SSID_AVAIL :
				case WL_IDLE_STATUS : 
					break;
					
				case WL_CONNECT_FAILED :
					fim = 1;
					break;
			}

			time(&now);

			if ( (now - start) > timeoutConexaoAsClient ) {
				fim = 1;
			}
		}

		if ( WiFi.status() == WL_CONNECTED) {
			strErroConexao = NULL;
	  		Serial.println(WiFi.localIP());
	  		
	  		return(1); // Conectado a rede configurada OK
		}
		
		switch(WiFi.status()) {
			case WL_NO_SSID_AVAIL 	: strErroConexao = "Não localizou SSID"; break;
			case WL_IDLE_STATUS 	: strErroConexao = "Timeout na conexão"; break;
			case WL_CONNECT_FAILED 	: strErroConexao = "Senha invalida"; break;
			default 				: strErroConexao = "Falha na conexão"; break;
		}
	}

    Serial.println(strErroConexao);

	// Não conseguiu conectar na rede configurada ou ainda não configurada
	// Vamos operar como AccessPoint esperando uma nova configuracao


IPAddress local_IP(192,168,4,1);
IPAddress gateway(192,168,4,1);
IPAddress subnet(255,255,255,0);

Serial.println(WiFi.softAPConfig(local_IP, gateway, subnet) ? "Ready" : "Failed!");

	if ( ! WiFi.softAP(FIXED_SSID_ACCESS_POINT, FIXED_PASSWD_ACCESS_POINT) ) {
		hardwareReset("Falha em  WiFi.softAP");
	}

//	Serial.println(WiFi.localIP());
//	Serial.println("WORKING_AS_ACCESS_POINT");
	
	
	Serial.print("Setting soft-AP configuration ... ");
	Serial.println(WiFi.softAPConfig(local_IP, gateway, subnet) ? "Ready" : "Failed!");
	
	Serial.print("Setting soft-AP ... ");
	Serial.println(WiFi.softAP(FIXED_SSID_ACCESS_POINT, FIXED_PASSWD_ACCESS_POINT) ? "Ready" : "Failed!");
	
	Serial.print("Soft-AP IP address = ");
	Serial.println(WiFi.softAPIP());
	
	Serial.println("WORKING_AS_ACCESS_POINT");
	
	return(0); // funcionando como Access Point
}
	



//---------------------------------------------------------
// salvaConfigEEPROM
//---------------------------------------------------------
void salvaConfigEEPROM(void)
{
	int indEEPROM=0;
	int indConfig;
	char *p;
	
	for ( indConfig = 1; indConfig <= PARAM_CONFIG_PASSWD; indConfig++) {
		switch(indConfig) {
			case PARAM_CONFIG_SSID   : p = strSSID; break;
			case PARAM_CONFIG_PASSWD : p = strPasswd; break;
			default : p = NULL;			
		}

		if ( p ) {
			EEPROM.write(indEEPROM++, '[');
			do {
				EEPROM.write(indEEPROM++, *p);
			} while ( *p++ != '\0');
			EEPROM.write(indEEPROM++, ']');
		}
	}
	
	EEPROM.commit();
}

//---------------------------------------------------------
// recuperaConfigEEPROM
//---------------------------------------------------------
void recuperaConfigEEPROM(void)
{	int linha=0;
	int indEEPROM=0;
	int indAux=0;
	char aux[MAX_LEN];
	unsigned char c;
	int indConfig=0;
	int erro=0;

	// Inicialização do tratamento da  EEPROM
	EEPROM.begin(128);	// Se aumentar quantidade de parametros de configuração, precisamos aumentar esse tamanho


	int fim = 0;
	while ( (erro == 0) && (fim==0) ) {
		c = EEPROM.read(indEEPROM++);

		if ( c == 255 ) break;

		if ( indAux >= MAX_LEN ) {
			erro = 1;
			break;
		}
		
		aux[indAux++] = c;
		
		if ( c == 0 ) {
			switch(++indConfig) {
				case PARAM_CONFIG_SSID  :
					if ( (aux[0] == '[') && (aux[indAux-1] == ']') ) {
						aux[indAux-1] = '\0';
						strcpy(strSSID, &aux[1]);
					} else {
						erro = 1;
					}
					break;
					
				case PARAM_CONFIG_PASSWD :
					if ( (aux[0] == '[') && (aux[indAux-1] == ']') ) {
						aux[indAux-1] = '\0';
						strcpy(strPasswd, &aux[1]);
					} else {
						erro = 1;
					}
					break;

				case PARAM_TIMEOUT_AS_CLIENT :
					if ( (aux[0] == '[') && (aux[indAux-1] == ']') ) {
						int val = atoi(&aux[1]);
						if ( (val > 0) && (val<=60) ) {
							timeoutConexaoAsClient = val;
						}
					} else {
						erro = 1;
					}
					break;

				case PARAM_TIMEOUT_AS_ACCESS_POINT :
					if ( (aux[0] == '[') && (aux[indAux-1] == ']') ) {
						int val = atoi(&aux[1]);
						if ( (val > 0) && (val<=60) ) {
							timeoutConexaoAsAccessPoint = val;
						}
					} else {
						erro = 1;
					}
					break;

				default: 
					fim = 1;
					break;
			}
			
			indAux = 0;
		}
	}

	if ( erro ) {
		strSSID[0] = '\0';
		strPasswd[0] = '\0';
	}
}


void hardwareReset( char *msg )
{	int tempo = 1000;

	Serial.println(msg);

	while ( tempo > 0 ) {
		digitalWrite(BUILT_IN_LED, HIGH); // Acende o Led
		delay(tempo); 
		digitalWrite(BUILT_IN_LED, LOW); // Apaga o Led
		delay(tempo); 
		tempo -= 200;
	}

	ESP.restart();
}
