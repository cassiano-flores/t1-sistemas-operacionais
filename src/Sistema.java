// PUCRS - Escola Politécnica - Sistemas Operacionais
// Prof. Fernando Dotti
// Código fornecido como parte da solução do projeto de Sistemas Operacionais
//
// VM
//    HW = memória, cpu
//    SW = tratamento int e chamada de sistema
// Funcionalidades de carga, execução e dump de memória

import java.util.*;

public class Sistema {

	// -------------------------------------------------------------------------------------------------------
	// --------------------- H A R D W A R E - definicoes de HW
	// ----------------------------------------------

	// -------------------------------------------------------------------------------------------------------
	// --------------------- M E M O R I A - definicoes de palavra de memoria,
	// memória ----------------------

	public class Memory {
		public int tamMem;
		public Word[] m; // m representa a memória fisica: um array de posicoes de memoria (word)

		public Memory(int size) {
			tamMem = size;
			m = new Word[tamMem];
			for (int i = 0; i < tamMem; i++) {
				m[i] = new Word(Opcode.___, -1, -1, -1);
			}
			;
		}

		public void dump(Word w) { // funcoes de DUMP nao existem em hardware - colocadas aqui para facilidade
			System.out.print("[ ");
			System.out.print(w.opc);
			System.out.print(", ");
			System.out.print(w.r1);
			System.out.print(", ");
			System.out.print(w.r2);
			System.out.print(", ");
			System.out.print(w.p);
			System.out.println("  ] ");
		}

		public void dump(int ini, int fim) {
			for (int i = ini; i < fim; i++) {
				System.out.print(i);
				System.out.print(":  ");
				dump(m[i]);
			}
		}
	}

	// -------------------------------------------------------------------------------------------------------

	public class Word { // cada posicao da memoria tem uma instrucao (ou um dado)
		public Opcode opc; //
		public int r1; // indice do primeiro registrador da operacao (Rs ou Rd cfe opcode na tabela)
		public int r2; // indice do segundo registrador da operacao (Rc ou Rs cfe operacao)
		public int p; // parametro para instrucao (k ou A cfe operacao), ou o dado, se opcode = DADO

		public Word(Opcode _opc, int _r1, int _r2, int _p) { // vide definição da VM - colunas vermelhas da tabela
			opc = _opc;
			r1 = _r1;
			r2 = _r2;
			p = _p;
		}
	}

	// -------------------------------------------------------------------------------------------------------
	// --------------------- C P U - definicoes da CPU
	// -----------------------------------------------------

	public enum Opcode {
		DATA, ___, // se memoria nesta posicao tem um dado, usa DATA, se nao usada ee NULO ___
		JMP, JMPI, JMPIG, JMPIL, JMPIE, // desvios e parada
		JMPIM, JMPIGM, JMPILM, JMPIEM, STOP,
		JMPIGK, JMPILK, JMPIEK, JMPIGT,
		ADDI, SUBI, ADD, SUB, MULT, // matematicos
		LDI, LDD, STD, LDX, STX, MOVE, // movimentacao
		TRAP // chamada de sistema
	}

	public enum Interrupts { // possiveis interrupcoes que esta CPU gera
		noInterrupt, intEnderecoInvalido, intInstrucaoInvalida, intOverflow, intSTOP, intRegistradorInvalido;
	}

	public class CPU {
		private int maxInt; // valores maximo e minimo para inteiros nesta cpu
		private int minInt;
		// característica do processador: contexto da CPU ...
		private int pc; // ... composto de program counter,
		private Word ir; // instruction register,
		private int[] reg; // registradores da CPU
		private Interrupts irpt; // durante instrucao, interrupcao pode ser sinalizada
		private int base; // base e limite de acesso na memoria
		private int limite; // por enquanto toda memoria pode ser acessada pelo processo rodando
							// ATE AQUI: contexto da CPU - tudo que precisa sobre o estado de um processo
							// para executa-lo
							// nas proximas versoes isto pode modificar

		private Memory mem; // mem tem funcoes de dump e o array m de memória 'fisica'
		private Word[] m; // CPU acessa MEMORIA, guarda referencia a 'm'. m nao muda. semre será um array
							// de palavras

		private InterruptHandling ih; // significa desvio para rotinas de tratamento de Int - se int ligada, desvia
		private SysCallHandling sysCall; // significa desvio para tratamento de chamadas de sistema - trap
		private boolean debug; // se true entao mostra cada instrucao em execucao

		public CPU(Memory _mem, InterruptHandling _ih, SysCallHandling _sysCall, boolean _debug) { // ref a MEMORIA e
																									// interrupt handler
																									// passada na
																									// criacao da CPU
			maxInt = 32767; // capacidade de representacao modelada
			minInt = -32767; // se exceder deve gerar interrupcao de overflow
			mem = _mem; // usa mem para acessar funcoes auxiliares (dump)
			m = mem.m; // usa o atributo 'm' para acessar a memoria.
			reg = new int[10]; // aloca o espaço dos registradores - regs 8 e 9 usados somente para IO
			ih = _ih; // aponta para rotinas de tratamento de int
			sysCall = _sysCall; // aponta para rotinas de tratamento de chamadas de sistema
			debug = _debug; // se true, print da instrucao em execucao

			base = 0; // limite inferior da memória
			limite = _mem.tamMem; // limite superior da memória
		}

		public void setDebug(boolean debug) {
			this.debug = debug;
		}

		// teste de memória (in)válida
		private boolean legal(int e) { // todo acesso a memoria tem que ser verificado
			// e fora dos limites permitidos de memória
			if (e < this.base || e >= this.limite) {
				irpt = Interrupts.intEnderecoInvalido;
				return false;
			}
			return true;
		}

		// teste de registrador (in)válido
		private boolean testaReg(int reg) {
			// se o registrador for invalido
			if (reg < 0 || reg >= 10) {
				irpt = Interrupts.intRegistradorInvalido;
				return false;
			}
			return true;
		}

		private boolean testOverflow(int v) { // toda operacao matematica deve avaliar se ocorre overflow
			if ((v < minInt) || (v > maxInt)) {
				irpt = Interrupts.intOverflow;
				return false;
			}
			;
			return true;
		}

		public void setContext(int _base, int _limite, int _pc) { // no futuro esta funcao vai ter que ser
			base = _base; // expandida para setar todo contexto de execucao,
			limite = _limite; // agora, setamos somente os registradores base,
			pc = _pc; // limite e pc (deve ser zero nesta versao)
			irpt = Interrupts.noInterrupt; // reset da interrupcao registrada
		}

		public void run() { // execucao da CPU supoe que o contexto da CPU, vide acima, esta devidamente
							// setado
			while (true) { // ciclo de instrucoes. acaba cfe instrucao, veja cada caso.
				// --------------------------------------------------------------------------------------------------
				// FETCH
				if (legal(pc)) { // pc valido
					ir = m[pc]; // <<<<<<<<<<<< busca posicao da memoria apontada por pc, guarda em ir
					if (debug) {
						System.out.print("                               pc: " + pc + "       exec: ");
						mem.dump(ir);
					}
					// --------------------------------------------------------------------------------------------------
					// EXECUTA INSTRUCAO NO ir
					switch (ir.opc) { // conforme o opcode (código de operação) executa

						// Instrucoes de Busca e Armazenamento em Memoria
						case LDI: // Rd ← k
							// testa se o r1 é válido
							if (!(testaReg(ir.r1))) {
								break;
							}
							reg[ir.r1] = ir.p;
							pc++;
							break;

						case LDD: // Rd <- [A]
							// testa se a pos de mem é válida
							if (legal(ir.p)) {
								// se for valida, testa se o r1 é valido
								if (!(testaReg(ir.r1))) {
									break;
								}
								reg[ir.r1] = m[ir.p].p;
								pc++;
							}
							break;

						case LDX: // RD <- [RS] // NOVA
							// testa se o r2 é válido
							if (!(testaReg(ir.r2))) {
								break;
							}
							// se o r2 for válido, testa se a posição de memoria é válida
							if (legal(reg[ir.r2])) {
								// se a posição de memoria for válida, testa se o r1 é valido
								if (!(testaReg(ir.r1))) {
									break;
								}
								reg[ir.r1] = m[reg[ir.r2]].p;
								pc++;
							}
							break;

						case STD: // [A] ← Rs
							// testa se o r1 é valido
							if (!(testaReg(ir.r1))) {
								break;
							}
							// se for, testa se a pos mem é valida
							if (legal(ir.p)) {
								m[ir.p].opc = Opcode.DATA;
								m[ir.p].p = reg[ir.r1];
								pc++;
							}
							;
							break;

						case STX: // [Rd] ←Rs
							// testa se r1 é válido
							if (!(testaReg(ir.r1))) {
								break;
							}
							// se for, testa se a pos mem é válida
							if (legal(reg[ir.r1])) {
								m[reg[ir.r1]].opc = Opcode.DATA;
								// se for, testa se o r2 é valido
								if (!(testaReg(ir.r2))) {
									break;
								}
								m[reg[ir.r1]].p = reg[ir.r2];
								pc++;
							}
							;
							break;

						case MOVE: // RD <- RS
							// testa se os registradores r1 e r2 são validos
							if (!(testaReg(ir.r1))) {
								break;
							}
							if (!(testaReg(ir.r2))) {
								break;
							}
							reg[ir.r1] = reg[ir.r2];
							pc++;
							break;

						// Instrucoes Aritmeticas
						case ADD: // Rd ← Rd + Rs
							// testa se r1 e r2 são válidos
							if (!testaReg(ir.r1)) {
								break;
							}
							if (!testaReg(ir.r2)) {
								break;
							}
							reg[ir.r1] = reg[ir.r1] + reg[ir.r2];
							testOverflow(reg[ir.r1]);
							pc++;
							break;

						case ADDI: // Rd ← Rd + k
							// testa se r1 é válido
							if (!testaReg(ir.r1)) {
								break;
							}
							reg[ir.r1] = reg[ir.r1] + ir.p;
							testOverflow(reg[ir.r1]);
							pc++;
							break;

						case SUB: // Rd ← Rd - Rs
							// testa se r1 e r2 são válidos
							if (!testaReg(ir.r1)) {
								break;
							}
							if (!testaReg(ir.r2)) {
								break;
							}
							reg[ir.r1] = reg[ir.r1] - reg[ir.r2];
							testOverflow(reg[ir.r1]);
							pc++;
							break;

						case SUBI: // RD <- RD - k // NOVA
							// testa se r1 é válido
							if (!testaReg(ir.r1)) {
								break;
							}
							reg[ir.r1] = reg[ir.r1] - ir.p;
							testOverflow(reg[ir.r1]);
							pc++;
							break;

						case MULT: // Rd <- Rd * Rs
							// testa se r1 e r2 são válidos
							if (!testaReg(ir.r1)) {
								break;
							}
							if (!testaReg(ir.r2)) {
								break;
							}
							reg[ir.r1] = reg[ir.r1] * reg[ir.r2];
							testOverflow(reg[ir.r1]);
							pc++;
							break;

						// Instrucoes JUMP
						case JMP: // PC <- k
							// testa se k é endereço de memória válido
							if (!legal(ir.p)) {
								break;
							}
							pc = ir.p;
							break;

						case JMPIG: // If Rc > 0 Then PC ← Rs Else PC ← PC +1
							// testa se r1 e r2 são válidos
							if (!testaReg(ir.r1)) {
								break;
							}
							if (!testaReg(ir.r2)) {
								break;
							}
							if (reg[ir.r2] > 0) {
								pc = reg[ir.r1];
							} else {
								pc++;
							}
							break;

						case JMPIGK: // If RC > 0 then PC <- k else PC++
							// testa se r2 é válido
							if (!testaReg(ir.r2)) {
								break;
							}
							// testa se o endereço de memoria é válido
							if (!legal(ir.p)) {
								break;
							}
							if (reg[ir.r2] > 0) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;

						case JMPILK: // If RC < 0 then PC <- k else PC++
							// testa se r2 é válido
							if (!testaReg(ir.r2)) {
								break;
							}
							// testa se o endereço é valido
							if (!legal(ir.p)) {
								break;
							}
							if (reg[ir.r2] < 0) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;

						case JMPIEK: // If RC = 0 then PC <- k else PC++
							// testa se o r2 é valido
							if (!testaReg(ir.r2)) {
								break;
							}
							// testa se endereço é válido
							if (!legal(ir.p)) {
								break;
							}
							if (reg[ir.r2] == 0) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;

						case JMPIL: // if Rc < 0 then PC <- Rs Else PC <- PC +1
							// testa se r1 e r2 são válidos
							if (!testaReg(ir.r2)) {
								break;
							}
							if (!testaReg(ir.r1)) {
								break;
							}
							if (reg[ir.r2] < 0) {
								pc = reg[ir.r1];
							} else {
								pc++;
							}
							break;

						case JMPIE: // If Rc = 0 Then PC <- Rs Else PC <- PC +1
							// testa se r1 e r2 são válidos
							if (!testaReg(ir.r2)) {
								break;
							}
							if (!testaReg(ir.r1)) {
								break;
							}
							if (reg[ir.r2] == 0) {
								pc = reg[ir.r1];
							} else {
								pc++;
							}
							break;

						case JMPIM: // PC <- [A]
							// testa se endereço é válido
							if (!legal(ir.p)) {
								break;
							}
							pc = m[ir.p].p;
							break;

						case JMPIGM: // If RC > 0 then PC <- [A] else PC++
							// testa se r2 é válido
							if (!testaReg(ir.r2)) {
								break;
							}
							// testa se ´posição de memória é válida
							if (!legal(ir.p)) {
								break;
							}
							if (reg[ir.r2] > 0) {
								pc = m[ir.p].p;
							} else {
								pc++;
							}
							break;

						case JMPILM: // If RC < 0 then PC <- k else PC++
							// testa se r2 é válido
							if (!testaReg(ir.r2)) {
								break;
							}
							// testa se posição de memória é válida
							if (!legal(ir.p)) {
								break;
							}
							if (reg[ir.r2] < 0) {
								pc = m[ir.p].p;
							} else {
								pc++;
							}
							break;

						case JMPIEM: // If RC = 0 then PC <- k else PC++
							// testa se r2 é valido
							if (!testaReg(ir.r2)) {
								break;
							}
							if (!legal(ir.p)) {
								break;
							}
							if (reg[ir.r2] == 0) {
								pc = m[ir.p].p;
							} else {
								pc++;
							}
							break;

						case JMPIGT: // If RS>RC then PC <- k else PC++
							// testa se r1 e r2 são válidos
							if (!testaReg(ir.r1)) {
								break;
							}
							if (!testaReg(ir.r2)) {
								break;
							}
							if (reg[ir.r1] > reg[ir.r2]) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;

						// outras
						case STOP: // por enquanto, para execucao
							irpt = Interrupts.intSTOP;
							break;

						case DATA:
							irpt = Interrupts.intInstrucaoInvalida;
							break;

						// Chamada de sistema
						case TRAP:
							sysCall.handle(); // <<<<< aqui desvia para rotina de chamada de sistema, no momento so
												// temos IO
							pc++;
							break;

						// Inexistente
						default:
							irpt = Interrupts.intInstrucaoInvalida;
							break;
					}
				}
				// --------------------------------------------------------------------------------------------------
				// VERIFICA INTERRUPÇÃO !!! - TERCEIRA FASE DO CICLO DE INSTRUÇÕES
				if (!(irpt == Interrupts.noInterrupt)) { // existe interrupção
					ih.handle(irpt, pc); // desvia para rotina de tratamento
					break; // break sai do loop da cpu
				}
			} // FIM DO CICLO DE UMA INSTRUÇÃO
		}
	}
	// ------------------ C P U - fim
	// ------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------

	// ------------------- V M - constituida de CPU e MEMORIA
	// -----------------------------------------------
	// -------------------------- atributos e construcao da VM
	// -----------------------------------------------
	public class VM {
		public int tamMem;
		public Word[] m;
		public Memory mem;
		public CPU cpu;

		public VM(InterruptHandling ih, SysCallHandling sysCall, int tamMem) {
			// vm deve ser configurada com endereço de tratamento de interrupcoes e de
			// chamadas de sistema
			// cria memória
			this.tamMem = tamMem;
			mem = new Memory(tamMem);
			m = mem.m;
			// cria cpu
			cpu = new CPU(mem, ih, sysCall, true); // true liga debug
		}

		public void setDebug(boolean debug) {
			this.cpu.setDebug(debug);
		}

	}
	// ------------------- V M - fim
	// ------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------

	// --------------------H A R D W A R E - fim
	// -------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------

	// -------------------------------------------------------------------------------------------------------

	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// ------------------- S O F T W A R E - inicio
	// ----------------------------------------------------------

	// ------------------- I N T E R R U P C O E S - rotinas de tratamento
	// ----------------------------------
	public class InterruptHandling {
		public void handle(Interrupts irpt, int pc) { // apenas avisa - todas interrupcoes neste momento finalizam o
														// programa
			System.out.println("                                               Interrupcao " + irpt + "   pc: " + pc);
			switch (irpt) {
				case intEnderecoInvalido:
					System.out.println("Ocorreu a interrupção " + irpt + ", na linha pc: " + pc);

					break;
				case intInstrucaoInvalida:
					System.out.println("Ocorreu a interrupção " + irpt + ", na linha pc: " + pc);
					break;

				case intOverflow:
					System.out.println("Ocorreu a interrupção " + irpt + ", na linha pc: " + pc);

					break;

				case intRegistradorInvalido:
					System.out.println("Ocorreu a interrupção " + irpt + ", na linha pc: " + pc);

					break;

				case intSTOP:
					System.out.println("Ocorreu a interrupção " + irpt + ", na linha pc: " + pc);

				default:
					break;
			}
		}
	}

	// ------------------- C H A M A D A S D E S I S T E M A - rotinas de tratamento
	// ----------------------
	public class SysCallHandling {
		private VM vm;

		public void setVM(VM _vm) {
			vm = _vm;
		}

		public void handle() { // apenas avisa - todas interrupcoes neste momento finalizam o programa
			System.out.println("                                               Chamada de Sistema com op  /  par:  "
					+ vm.cpu.reg[8] + " / " + vm.cpu.reg[9]);
			int endereco;
			// reg[8] da cpu contem o id da syscall
			switch (vm.cpu.reg[8]) {
				// entrada
				case 1:
					// endereço de destino
					endereco = vm.cpu.reg[9];
					Scanner in = new Scanner(System.in);
					int temp = in.nextInt();
					vm.mem.m[endereco].opc = Opcode.DATA;
					vm.mem.m[endereco].p = temp;
					in.close();
					break;

				// saída
				case 2:
					// endereço fonte
					endereco = vm.cpu.reg[9];
					System.out.println(vm.mem.m[endereco].p);
					break;

				default:
					System.out.println("Chamada inválida!");
					break;
			}
		}
	}

	// ------------------ U T I L I T A R I O S D O S I S T E M A
	// -----------------------------------------
	// ------------------ load é invocado a partir de requisição do usuário

	private void loadProgram(Word[] p, Word[] m) {
		for (int i = 0; i < p.length; i++) {
			m[i].opc = p[i].opc;
			m[i].r1 = p[i].r1;
			m[i].r2 = p[i].r2;
			m[i].p = p[i].p;
		}
	}

	private void loadProgram(Word[] p) {
		loadProgram(p, vm.m);
	}

	private void loadAndExec(Word[] p) {
		// cria processo

		loadProgram(p); // carga do programa na memoria
		System.out.println("---------------------------------- programa carregado na memoria");
		vm.mem.dump(0, p.length); // dump da memoria nestas posicoes
		vm.cpu.setContext(0, vm.tamMem - 1, 0); // seta estado da cpu ]
		System.out.println("---------------------------------- inicia execucao ");
		vm.cpu.run(); // cpu roda programa ate parar
		System.out.println("---------------------------------- memoria após execucao ");
		vm.mem.dump(0, p.length); // dump da memoria com resultado
	}

	// -------------------------------------------------------------------------------------------------------
	// ------------------- S I S T E M A
	// --------------------------------------------------------------------

	public VM vm;
	public InterruptHandling ih;
	public SysCallHandling sysCall;
	public static Programas progs;
	public PCB rodando;
	public ArrayList<PCB> listaAptos;

	public PCB pcbclasse;
	public GM gerenteMem;
	public GP gerentePro;

	public Sistema() { // a VM com tratamento de interrupções
		int tamMem = 1024;
		int tamPart = 64;
		ih = new InterruptHandling();
		sysCall = new SysCallHandling();
		vm = new VM(ih, sysCall, tamMem);
		sysCall.setVM(vm);
		progs = new Programas();

		listaAptos = new ArrayList<>();
		gerenteMem = new GM();
		gerentePro = new GP();
		pcbclasse = new PCB();

		gerenteMem.init(tamMem, tamPart);
	}

	/**
	 * Gerente de memória
	 */
	public static class GM {

		private int nroParticao;
		private static boolean[] frame;
		public static int tamPart;
		public int tamMem;

		public void init(int tamMem, int tamPart) {
			this.tamPart = tamPart;
			this.tamMem = tamMem;
			// define o nro de partições
			this.nroParticao = tamMem / tamPart;
			// aloca o array de frames
			frame = new boolean[nroParticao];
			// seta todos como livre
			for (int i = 0; i < frame.length; i++) {
				frame[i] = true;
			}
		}

		public static int[] aloca(int tamProg) {
			int[] result = { -1, -1 };
			// se o programa for maior que o tamanho da partição
			if (tamProg > tamPart) {
				return result;
			}

			// percorre o array
			for (int i = 0; i < frame.length; i++) {
				// se um frame estiver livre
				if (frame[i]) {
					// marca como válido
					result[0] = 1;
					// salva a partição
					result[1] = i;
					// marca como usando
					frame[i] = false;
					// retorna
					return result;
				}
			}
			return result;
		}

		// vetor aloca tem 2 posições,
		// a primeira define se é possivel alocar(true == 1) ou não é possível
		// alocar(false == -1)
		// e a segunda posição mostra qual o número da partição que foi alocada. (só
		// olhar caso na primeira posição for true

		public static void desaloca(int particao) {

			// se a partição for inválida
			if (particao < 0 || particao > frame.length) {
				System.out.println("Número de partição inválido");
			} else {
				// marca como livre
				frame[particao] = true;
			}
		}

		// obtém o endereço de memória inicial de uma partição
		private int iniPart(int part) {
			//
			return part * tamPart;
		}

		// obtém o endereço de memória final de uma partição
		private int fimPart(int part) {
			// obtém o endereço inicial da partição seguinte e subtrai 1
			return (part + 1) * tamPart - 1;
		}

		// endereço logico e partição
		public int traduzMem(int endLog, int part) {
			// se o endereço lógico for inválido
			if (endLog < 0 || endLog > tamPart) {
				// retorna valor inválido
				return -1;
			}
			// obtém a pos inicial da particao e soma o endereço lógico
			return iniPart(part) + endLog;

		}
	}

	/**
	 * Gerente de Processo
	 */
	public class GP {
		int id = 0;
		int invalido = -1;

		boolean criaProcesso(Word[] prog) {
			int tamProg = prog.length;
			System.out.println(tamProg);// controle
			PCB pcb;
			// se o programa cabe na partição
			if (GM.tamPart > tamProg) {
				// pede uma partição
				int[] result = GM.aloca(tamProg);
				// se não dá pra alocar
				if (result[0] == invalido) {
					return false;
				}
				// se der, cria um PCB do programa
				pcb = new PCB(result[1], 'c', tamProg, id);
				// incrementa o id geral
				id++;
				// adiciona processo na lista de prontos
				listaAptos.add(pcb);
				return true;
			}
			return false;
		}

		void desalocaProcesso(int pid) {
			for (PCB pcb : listaAptos) {
				// se o pcb da lista for o mesmo do parâmetro
				if (pcb.id == pid) {
					// desaloca a partição
					GM.desaloca(pcb.particao);
					// remove das listas
					listaAptos.remove(pcb);
					// desaloca o pcb
					pcb = null;
					// sai do for
					break;
				}
			}
		}

	}

	/**
	 * Process Control Block
	 */
	public class PCB {

		int particao;
		char estadoAtual;
		int tamanho;
		int id;
		int pc;

		public PCB() {
		}

		public PCB(int particao, char estadoAtual, int tamanho, int id) {
			this.particao = particao;
			this.estadoAtual = estadoAtual;
			this.tamanho = tamanho;
			this.id = id;
			this.pc = 0;
		}

		public void setEstadoAtual(char estadoAtual) {
			this.estadoAtual = estadoAtual;
		}
	}
	// ------------------- S I S T E M A - fim
	// --------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------

	// -------------------------------------------------------------------------------------------------------
	// ------------------- instancia e testa sistema
	public static void main(String args[]) {
		Sistema s = new Sistema();
		boolean exec = true;
		Scanner n = new Scanner(System.in);
		int key;

		while (exec) {
			System.out.println();
			System.out.println("Selecione operacao:");
			System.out.println("1 - Criar Processo");
			System.out.println("2 - Dump de Processo");
			System.out.println("3 - Desalocar Processo");
			System.out.println("4 - Dump de Memória");
			System.out.println("5 - Executar Processo");
			System.out.println("6 - TraceOn");
			System.out.println("7 - TraceOff");
			System.out.println("0 - Exit");

			key = n.nextInt();
			n.nextLine();

			switch (key) {
				case 1:// criar processo
					boolean procs = true;
					while (procs) {
						System.out.println("Selecione o programa:");
						System.out.println();
						System.out.println("1 - Fatorial");
						System.out.println("2 - ProgMinimo");
						System.out.println("3 - Fibonacci10");
						System.out.println("4 - Fatorial TRAP");
						System.out.println("5 - Fibonacci TRAP");
						System.out.println("6 - PB");
						System.out.println("7 - PC");

						int prog = n.nextInt();
						boolean result;
						switch (prog) {
							case 1:// Fatorial
								result = s.gerentePro.criaProcesso(progs.fatorial);
								// System.out.println(result);

								if (result) {
									System.out.println("Fatorial criado com sucesso!");
								}
								procs = false;
								break;

							case 2:// ProgMinimo
								result = s.gerentePro.criaProcesso(progs.progMinimo);
								// System.out.println(result);
								if (result) {
									System.out.println("ProgMinimo criado com sucesso!");
								}
								procs = false;
								break;

							case 3:// Fibonacci10
								result = s.gerentePro.criaProcesso(progs.fibonacci10);
								// System.out.println(result);
								if (result) {
									System.out.println("Fibonacci10 criado com sucesso!");
								}
								procs = false;
								break;

							case 4:// Fatorial TRAP
								result = s.gerentePro.criaProcesso(progs.fatorialTRAP);
								// System.out.println(result);
								if (result) {
									System.out.println("Fatorial TRAP criado com sucesso!");
								}
								procs = false;
								break;

							case 5:// Fibonacci TRAP
								result = s.gerentePro.criaProcesso(progs.fibonacciTRAP);
								// System.out.println(result);
								if (result) {
									System.out.println("Fibonacci TRAP criado com sucesso!");
								}
								procs = false;
								break;

							case 6:// PB
								result = s.gerentePro.criaProcesso(progs.PB);
								// System.out.println(result);
								if (result) {
									System.out.println("PB criado com sucesso!");
								}
								procs = false;
								break;

							case 7:// PC
								result = s.gerentePro.criaProcesso(progs.PC);
								// System.out.println(result);
								if (result) {
									System.out.println("PC criado com sucesso!");
								}
								procs = false;
								break;

							default:
								break;
						}

					}

					break;

				case 2:// dump processo

					break;

				case 3:// desaloca processo
					System.out.println("Digite o ID do processo a ser desalocado!");
					int id = n.nextInt();
					s.gerentePro.desalocaProcesso(id);
					System.out.println("Processo desalocado!");

					break;

				case 4:// demp de memória

					break;

				case 5:// executar processo

					s.loadAndExec(progs.fatorial);
					break;

				case 6:// trace on
					s.vm.setDebug(true);
					System.out.println("Trace ativado");
					break;

				case 7:// trace off
					s.vm.setDebug(false);
					System.out.println("Trace desativado");
					break;

				case 0:// sair
					exec = false;
					break;

				default:
					break;
			}

		}

		// s.loadAndExec(progs.fibonacci10);
		// s.loadAndExec(progs.progMinimo);
		//s.loadAndExec(progs.fatorial);
		// s.loadAndExec(progs.fatorialTRAP); // saida
		// s.loadAndExec(progs.fibonacciTRAP); // entrada
		// s.loadAndExec(progs.PC); // bubble sort

	}

	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// --------------- P R O G R A M A S - não fazem parte do sistema
	// esta classe representa programas armazenados (como se estivessem em disco)
	// que podem ser carregados para a memória (load faz isto)

	public class Programas {
		public Word[] fatorial = new Word[] {
				// este fatorial so aceita valores positivos. nao pode ser zero
				// linha coment
				new Word(Opcode.LDI, 0, -1, 4), // 0 r0 é valor a calcular fatorial
				new Word(Opcode.LDI, 1, -1, 1), // 1 r1 é 1 para multiplicar (por r0)
				new Word(Opcode.LDI, 6, -1, 1), // 2 r6 é 1 para ser o decremento
				new Word(Opcode.LDI, 7, -1, 8), // 3 r7 tem posicao de stop do programa = 8
				new Word(Opcode.JMPIE, 7, 0, 0), // 4 se r0=0 pula para r7(=8)
				new Word(Opcode.MULT, 1, 0, -1), // 5 r1 = r1 * r0
				new Word(Opcode.SUB, 0, 6, -1), // 6 decrementa r0 1
				new Word(Opcode.JMP, -1, -1, 4), // 7 vai p posicao 4
				new Word(Opcode.STD, 1, -1, 10), // 8 coloca valor de r1 na posição 10
				new Word(Opcode.STOP, -1, -1, -1), // 9 stop
				new Word(Opcode.DATA, -1, -1, -1) }; // 10 ao final o valor do fatorial estará na posição 10 da memória

		public Word[] progMinimo = new Word[] {
				new Word(Opcode.LDI, 0, -1, 999),
				new Word(Opcode.STD, 0, -1, 10),
				new Word(Opcode.STD, 0, -1, 11),
				new Word(Opcode.STD, 0, -1, 12),
				new Word(Opcode.STD, 0, -1, 13),
				new Word(Opcode.STD, 0, -1, 14),
				new Word(Opcode.STOP, -1, -1, -1) };

		public Word[] fibonacci10 = new Word[] { // mesmo que prog exemplo, so que usa r0 no lugar de r8
				new Word(Opcode.LDI, 1, -1, 0),
				new Word(Opcode.STD, 1, -1, 20),
				new Word(Opcode.LDI, 2, -1, 1),
				new Word(Opcode.STD, 2, -1, 21),
				new Word(Opcode.LDI, 0, -1, 22),
				new Word(Opcode.LDI, 6, -1, 6),
				new Word(Opcode.LDI, 7, -1, 31),
				new Word(Opcode.LDI, 3, -1, 0),
				new Word(Opcode.ADD, 3, 1, -1),
				new Word(Opcode.LDI, 1, -1, 0),
				new Word(Opcode.ADD, 1, 2, -1),
				new Word(Opcode.ADD, 2, 3, -1),
				new Word(Opcode.STX, 0, 2, -1),
				new Word(Opcode.ADDI, 0, -1, 1),
				new Word(Opcode.SUB, 7, 0, -1),
				new Word(Opcode.JMPIG, 6, 7, -1),
				new Word(Opcode.STOP, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1), // POS 20
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1) }; // ate aqui - serie de fibonacci ficara armazenada

		public Word[] fatorialTRAP = new Word[] {
				new Word(Opcode.LDI, 0, -1, 7), // numero para colocar na memoria
				new Word(Opcode.STD, 0, -1, 50),
				new Word(Opcode.LDD, 0, -1, 50),
				new Word(Opcode.LDI, 1, -1, -1),
				new Word(Opcode.LDI, 2, -1, 13), // SALVAR POS STOP
				new Word(Opcode.JMPIL, 2, 0, -1), // caso negativo pula pro STD
				new Word(Opcode.LDI, 1, -1, 1),
				new Word(Opcode.LDI, 6, -1, 1),
				new Word(Opcode.LDI, 7, -1, 13),
				new Word(Opcode.JMPIE, 7, 0, 0), // POS 9 pula pra STD (Stop-1)
				new Word(Opcode.MULT, 1, 0, -1),
				new Word(Opcode.SUB, 0, 6, -1),
				new Word(Opcode.JMP, -1, -1, 9), // pula para o JMPIE
				new Word(Opcode.STD, 1, -1, 18),
				new Word(Opcode.LDI, 8, -1, 2), // escrita
				new Word(Opcode.LDI, 9, -1, 18), // endereco com valor a escrever
				new Word(Opcode.TRAP, -1, -1, -1),
				new Word(Opcode.STOP, -1, -1, -1), // POS 17
				new Word(Opcode.DATA, -1, -1, -1) };// POS 18

		public Word[] fibonacciTRAP = new Word[] { // mesmo que prog exemplo, so que usa r0 no lugar de r8
				new Word(Opcode.LDI, 8, -1, 1), // leitura
				new Word(Opcode.LDI, 9, -1, 100), // endereco a guardar
				new Word(Opcode.TRAP, -1, -1, -1),
				new Word(Opcode.LDD, 7, -1, 100), // numero do tamanho do fib
				new Word(Opcode.LDI, 3, -1, 0),
				new Word(Opcode.ADD, 3, 7, -1),
				new Word(Opcode.LDI, 4, -1, 36), // posicao para qual ira pular (stop) *
				new Word(Opcode.LDI, 1, -1, -1), // caso negativo
				new Word(Opcode.STD, 1, -1, 41),
				new Word(Opcode.JMPIL, 4, 7, -1), // pula pra stop caso negativo *
				new Word(Opcode.JMPIE, 4, 7, -1), // pula pra stop caso 0
				new Word(Opcode.ADDI, 7, -1, 41), // fibonacci + posição do stop
				new Word(Opcode.LDI, 1, -1, 0),
				new Word(Opcode.STD, 1, -1, 41), // 25 posicao de memoria onde inicia a serie de fibonacci gerada
				new Word(Opcode.SUBI, 3, -1, 1), // se 1 pula pro stop
				new Word(Opcode.JMPIE, 4, 3, -1),
				new Word(Opcode.ADDI, 3, -1, 1),
				new Word(Opcode.LDI, 2, -1, 1),
				new Word(Opcode.STD, 2, -1, 42),
				new Word(Opcode.SUBI, 3, -1, 2), // se 2 pula pro stop
				new Word(Opcode.JMPIE, 4, 3, -1),
				new Word(Opcode.LDI, 0, -1, 43),
				new Word(Opcode.LDI, 6, -1, 25), // salva posição de retorno do loop
				new Word(Opcode.LDI, 5, -1, 0), // salva tamanho
				new Word(Opcode.ADD, 5, 7, -1),
				new Word(Opcode.LDI, 7, -1, 0), // zera (inicio do loop)
				new Word(Opcode.ADD, 7, 5, -1), // recarrega tamanho
				new Word(Opcode.LDI, 3, -1, 0),
				new Word(Opcode.ADD, 3, 1, -1),
				new Word(Opcode.LDI, 1, -1, 0),
				new Word(Opcode.ADD, 1, 2, -1),
				new Word(Opcode.ADD, 2, 3, -1),
				new Word(Opcode.STX, 0, 2, -1),
				new Word(Opcode.ADDI, 0, -1, 1),
				new Word(Opcode.SUB, 7, 0, -1),
				new Word(Opcode.JMPIG, 6, 7, -1), // volta para o inicio do loop
				new Word(Opcode.STOP, -1, -1, -1), // POS 36
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1), // POS 41
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1)
		};

		public Word[] PB = new Word[] {
				// dado um inteiro em alguma posição de memória,
				// se for negativo armazena -1 na saída; se for positivo responde o fatorial do
				// número na saída
				new Word(Opcode.LDI, 0, -1, 7), // numero para colocar na memoria
				new Word(Opcode.STD, 0, -1, 50),
				new Word(Opcode.LDD, 0, -1, 50),
				new Word(Opcode.LDI, 1, -1, -1),
				new Word(Opcode.LDI, 2, -1, 13), // SALVAR POS STOP
				new Word(Opcode.JMPIL, 2, 0, -1), // caso negativo pula pro STD
				new Word(Opcode.LDI, 1, -1, 1),
				new Word(Opcode.LDI, 6, -1, 1),
				new Word(Opcode.LDI, 7, -1, 13),
				new Word(Opcode.JMPIE, 7, 0, 0), // POS 9 pula pra STD (Stop-1)
				new Word(Opcode.MULT, 1, 0, -1),
				new Word(Opcode.SUB, 0, 6, -1),
				new Word(Opcode.JMP, -1, -1, 9), // pula para o JMPIE
				new Word(Opcode.STD, 1, -1, 15),
				new Word(Opcode.STOP, -1, -1, -1), // POS 14
				new Word(Opcode.DATA, -1, -1, -1) }; // POS 15

		public Word[] PC = new Word[] {
				// Para um N definido (10 por exemplo)
				// o programa ordena um vetor de N números em alguma posição de memória;
				// ordena usando bubble sort
				// loop ate que não swap nada
				// passando pelos N valores
				// faz swap de vizinhos se da esquerda maior que da direita
				new Word(Opcode.LDI, 7, -1, 5), // TAMANHO DO BUBBLE SORT (N)
				new Word(Opcode.LDI, 6, -1, 5), // aux N
				new Word(Opcode.LDI, 5, -1, 46), // LOCAL DA MEMORIA
				new Word(Opcode.LDI, 4, -1, 47), // aux local memoria
				new Word(Opcode.LDI, 0, -1, 4), // colocando valores na memoria
				new Word(Opcode.STD, 0, -1, 46),
				new Word(Opcode.LDI, 0, -1, 3),
				new Word(Opcode.STD, 0, -1, 47),
				new Word(Opcode.LDI, 0, -1, 5),
				new Word(Opcode.STD, 0, -1, 48),
				new Word(Opcode.LDI, 0, -1, 1),
				new Word(Opcode.STD, 0, -1, 49),
				new Word(Opcode.LDI, 0, -1, 2),
				new Word(Opcode.STD, 0, -1, 50), // colocando valores na memoria até aqui - POS 13
				new Word(Opcode.LDI, 3, -1, 25), // Posicao para pulo CHAVE 1
				new Word(Opcode.STD, 3, -1, 99),
				new Word(Opcode.LDI, 3, -1, 22), // Posicao para pulo CHAVE 2
				new Word(Opcode.STD, 3, -1, 98),
				new Word(Opcode.LDI, 3, -1, 38), // Posicao para pulo CHAVE 3
				new Word(Opcode.STD, 3, -1, 97),
				new Word(Opcode.LDI, 3, -1, 25), // Posicao para pulo CHAVE 4 (não usada)
				new Word(Opcode.STD, 3, -1, 96),
				new Word(Opcode.LDI, 6, -1, 0), // r6 = r7 - 1 POS 22
				new Word(Opcode.ADD, 6, 7, -1),
				new Word(Opcode.SUBI, 6, -1, 1), // ate aqui
				new Word(Opcode.JMPIEM, -1, 6, 97), // CHAVE 3 para pular quando r7 for 1 e r6 0 para interomper o loop
													// de vez do programa
				new Word(Opcode.LDX, 0, 5, -1), // r0 e r1 pegando valores das posições da memoria POS 26
				new Word(Opcode.LDX, 1, 4, -1),
				new Word(Opcode.LDI, 2, -1, 0),
				new Word(Opcode.ADD, 2, 0, -1),
				new Word(Opcode.SUB, 2, 1, -1),
				new Word(Opcode.ADDI, 4, -1, 1),
				new Word(Opcode.SUBI, 6, -1, 1),
				new Word(Opcode.JMPILM, -1, 2, 99), // LOOP chave 1 caso neg procura prox
				new Word(Opcode.STX, 5, 1, -1),
				new Word(Opcode.SUBI, 4, -1, 1),
				new Word(Opcode.STX, 4, 0, -1),
				new Word(Opcode.ADDI, 4, -1, 1),
				new Word(Opcode.JMPIGM, -1, 6, 99), // LOOP chave 1 POS 38
				new Word(Opcode.ADDI, 5, -1, 1),
				new Word(Opcode.SUBI, 7, -1, 1),
				new Word(Opcode.LDI, 4, -1, 0), // r4 = r5 + 1 POS 41
				new Word(Opcode.ADD, 4, 5, -1),
				new Word(Opcode.ADDI, 4, -1, 1), // ate aqui
				new Word(Opcode.JMPIGM, -1, 7, 98), // LOOP chave 2
				new Word(Opcode.STOP, -1, -1, -1), // POS 45
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1) };
	}
}