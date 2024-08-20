package chess;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class Chess_Bot {
    public static int WHITE_KING = 0;
    public static int WHITE_PAWN = 1;
    public static int WHITE_ROOK = 2;
    public static int WHITE_BISHOP = 3;
    public static int WHITE_KNIGHT = 4;
    public static int WHITE_QUEEN = 5;

    public static int BLACK_KING = 6;
    public static int BLACK_PAWN = 7;
    public static int BLACK_ROOK = 8;
    public static int BLACK_BISHOP = 9;
    public static int BLACK_KNIGHT = 10;
    public static int BLACK_QUEEN = 11;

    public static int EN_PASS = 12;
    public static int INFO = 13;

    public static int MAX_ITER = 5;
    public static int last_max_iter = 7;
    public static int MAX_TIME = 100;

    public static int move_array_size = 150;
    public static int CHECK_MATE_SCORE = 10000;

    public static int EVAL_CUTOFF = 200;
    public static int MIN_MOVES_TO_SEARCH = 3;

    public static long our_side;
    public static boolean check_mate_found = false;
    public static Map<Long,Integer> eval_map = new ConcurrentHashMap<>();
    public static Map<Long,DeltaMove[]> move_map = new ConcurrentHashMap<>();
    public static int positions_searched = 0;
    public static ExecutorService exService;
    public static int turn_counter = 0;

    public static void main(String args[]){
        board_state board = Chess.read_FEN("r1b1rk2/pp2bnpp/5N2/3q4/8/5N2/PP3PPP/R1B2RK1 w - - 0 1");
        Chess.seconds[1] = 5;
        get_bot_move(board);
        
    }

    public static int[] get_bot_move(board_state board){
        
        positions_searched = 0;
        eval_map.clear();
        move_map.clear();
        long[] bits = bit_board_maker(board);

        long hash = hash(bits);
        long[] openers = Consts.openings.get(hash);
        if (openers != null){
            long move;
            if (openers[1] != 0L){
                Random random = new Random();
                move = openers[random.nextInt(2)];
            } else {
                move = openers[0];
            }
            int piece = -1;
            if ((bits[INFO] & 0b10000L) == 0){
                for (int pc = BLACK_KING; pc <= BLACK_QUEEN; pc++){
                    if ((bits[pc] & move) != 0){
                        piece = pc;
                        break;
                    }
                }
            } else {
                for (int pc = WHITE_KING; pc <= WHITE_QUEEN; pc++){
                    if ((bits[pc] & move) != 0){
                        piece = pc;
                        break;
                    }
                }
            }
            DeltaMove delta = new DeltaMove(bits,move,piece);
            delta.swap(bits);
            return bit_to_int_board(delta, bits);
        }

        DeltaMove[] moves = get_initial_deltas(bits);
        long start_time = System.currentTimeMillis();
        boolean keep_going = true;
        // long max_time = Chess.use_clock ? (int) Chess.seconds[1] * 1000 / 40 : (int) Chess.seconds[1] * 1000;
        Random random = new Random();
        long max_time = (long)(random.nextDouble() * 3000L) + 2000L;
        long time_left;
        int iter = 1;
        
        int numThreads = Runtime.getRuntime().availableProcessors() - 1;
        exService = Executors.newFixedThreadPool(numThreads);

        while (keep_going){
            long sub_time = System.currentTimeMillis(); 
            
            time_left = max_time - (sub_time - start_time);
            System.out.println("TIME_LEFT " + time_left);
            sort_deltas(moves, true);

            keep_going = threader(bits,moves,iter,time_left);
            if (!keep_going) break;
            
            iter++;
        }

        exService.shutdownNow();
        

        DeltaMove best_move = moves[0];
        print_report(start_time,0L,bit_to_int_board(best_move, bits),best_move.eval,false);
        last_max_iter = iter;

        return bit_to_int_board(best_move, bits);
    }

    static boolean threader(long[] bits, DeltaMove[] moves, int iter, long time_out){
        
        MAX_ITER = iter;
        System.out.println(MAX_ITER);

        List<Callable<String>> tasks = new ArrayList<>();

        for (int i = 0; i < moves.length; i++){
            int id = i;
            tasks.add(() -> {
                long[] this_board = Arrays.copyOf(bits,bits.length);
                moves[id].swap(this_board);
                moves[id].eval = search(this_board,1,Integer.MIN_VALUE,Integer.MAX_VALUE);
                moves[id].swap(this_board);
                return "Task " + id + " complete";
            });
        }

        try {
            List<Future<String>> futures = exService.invokeAll(tasks, time_out, TimeUnit.MILLISECONDS);

            for (Future<String> future : futures) {
                if (future.isCancelled()) return false;   
            }

        } catch (InterruptedException e) {
            System.err.println("Execution interrupted: " + e.getMessage());
        }

        return true;
    }

    static void print_long(long inp){
        String num = String.format("%64s", Long.toBinaryString(inp)).replace(' ', '0');
        
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < 64; i++){
            sb.append(num.charAt(i));
            sb.append(' ');
            if ((i + 1)%8 == 0) sb.append('\n');
        }

        System.out.println(sb.toString());
    }

    static long[] bit_board_maker(board_state inp_board){
        long[] board = new long[14];
        for (Map.Entry<Integer,Character> piece : inp_board.map.entrySet()){
            switch(piece.getValue()) {
                case 'K' -> board[WHITE_KING] |= 1L << 63 - (piece.getKey()/10 + piece.getKey()%10 * 8);
                case 'Q' -> board[WHITE_QUEEN] |= 1L << 63 - (piece.getKey()/10 + piece.getKey()%10 * 8);
                case 'R' -> board[WHITE_ROOK] |= 1L << 63 - (piece.getKey()/10 + piece.getKey()%10 * 8);
                case 'B' -> board[WHITE_BISHOP] |= 1L << 63 - (piece.getKey()/10 + piece.getKey()%10 * 8);
                case 'N' -> board[WHITE_KNIGHT] |= 1L << 63 - (piece.getKey()/10 + piece.getKey()%10 * 8);
                case 'P' -> board[WHITE_PAWN] |= 1L << 63 - (piece.getKey()/10 + piece.getKey()%10 * 8);
                case 'k' -> board[BLACK_KING] |= 1L << 63 - (piece.getKey()/10 + piece.getKey()%10 * 8);
                case 'q' -> board[BLACK_QUEEN] |= 1L << 63 - (piece.getKey()/10 + piece.getKey()%10 * 8);
                case 'r' -> board[BLACK_ROOK] |= 1L << 63 - (piece.getKey()/10 + piece.getKey()%10 * 8);
                case 'b' -> board[BLACK_BISHOP] |= 1L << 63 - (piece.getKey()/10 + piece.getKey()%10 * 8);
                case 'n' -> board[BLACK_KNIGHT] |= 1L << 63 - (piece.getKey()/10 + piece.getKey()%10 * 8);
                case 'p' -> board[BLACK_PAWN] |= 1L << 63 - (piece.getKey()/10 + piece.getKey()%10 * 8);
            }
        }
        
        if (inp_board.en_passent_square != -1) board[EN_PASS] = 1L << 63 - (inp_board.en_passent_square/10 + inp_board.en_passent_square%10 * 8);
        
        if (inp_board.turn == 'w')   board[INFO] |= 0b10000L;
        if (inp_board.can_castle[0]) board[INFO] |= 0b01000L;
        if (inp_board.can_castle[1]) board[INFO] |= 0b00100L;
        if (inp_board.can_castle[2]) board[INFO] |= 0b00010L;
        if (inp_board.can_castle[3]) board[INFO] |= 0b00001L;
        return board;
    }

    public static DeltaMove[] get_initial_deltas(long[] board){
        our_side = board[INFO] & 0b10000L;
        int piece = (our_side != 0) ? WHITE_KING : BLACK_KING;
        int king = piece;

        long[] moves = get_moves(board);
        DeltaMove[] deltas = new DeltaMove[(int) moves[0]]; 

        int i = 1;
        int not_null_index = -1;

        while (moves[i++] != Consts.RANK_1){
            
            if (Long.bitCount(moves[i]) != 2){
                if (moves[i] == Consts.HEADER) piece++;
                continue;
            }
            
            deltas[++not_null_index] = new DeltaMove(board, moves[i], piece);
            if (is_sq_in_check(board, board[king], ((~board[INFO]) & 0b10000L)) == board[king]) {
                deltas[not_null_index].swap(board);
                deltas[not_null_index] = null;
                not_null_index--;
                continue;
            }

            deltas[not_null_index].swap(board);
        }

        return Arrays.copyOf(deltas,not_null_index + 1);
    }

    static void print_report(long start_time, long sub_time, int[] int_move, int best_eval, boolean canceled){
        System.out.println("\n\n-------------------------------");
        System.out.println("Depth              | " + MAX_ITER);
        System.out.println("Move               | " + Chess.pos_to_notation(int_move[0]) + " " + Chess.pos_to_notation(int_move[1]));
        System.out.println("Positions Searched | " + String.format("%,d", positions_searched));
        System.out.println("Evaluation         | " + best_eval);
        System.out.println("Total Time Taken   | " + (System.currentTimeMillis() - start_time) / 1000.0 + " s");
        System.out.println("Timed Out          | " + (canceled ? "TIMED OUT" : "No time out."));
        System.out.println("-------------------------------\n\n");
    }

    static int[] bit_to_int_board(DeltaMove move, long[] board){

        if (move.primary == WHITE_PAWN && Long.bitCount(move.primary_move) == 1) move.primary_move |= move.secondary_move;
        else if (move.primary == BLACK_PAWN && Long.bitCount(move.primary_move) == 1) move.primary_move |= move.secondary_move >>> 8;
        int[] pos = new int[2];
        pos[0] = (Long.numberOfLeadingZeros((move.primary_move & board[move.primary])) / 8) + Long.numberOfLeadingZeros((move.primary_move & board[move.primary])) % 8 * 10;
        pos[1] = (Long.numberOfLeadingZeros((move.primary_move & ~board[move.primary])) / 8) + Long.numberOfLeadingZeros((move.primary_move & ~board[move.primary])) % 8 * 10;
        return pos;
    }
    
    static int search(long[] board, int iter, int alpha, int beta){
        positions_searched++;
        if (iter >= MAX_ITER){
            return evaluate(board);
        }
        
        long[] moves = get_moves(board);
        int i = 1;
        int piece = ((board[INFO] & 0b10000L) != 0) ? WHITE_KING : BLACK_KING;
        int king = piece;
        int not_null_index = -1;
        
        DeltaMove[] deltas = new DeltaMove[(int) moves[0]];
        while (moves[i++] != Consts.RANK_1){
            if (Long.bitCount(moves[i]) != 2){
                if (moves[i] == Consts.HEADER) piece++;
                continue;
            }
            
            deltas[++not_null_index] = new DeltaMove(board, moves[i], piece);
            if (is_sq_in_check(board, board[king], ((~board[INFO]) & 0b10000L)) == board[king]) {
                deltas[not_null_index].swap(board);
                deltas[not_null_index] = null;
                not_null_index--;
                continue;
            }

            deltas[not_null_index].eval = evaluate(board);
            deltas[not_null_index].swap(board);
        }

        if (not_null_index == -1) {
            if (is_sq_in_check(board, board[(board[INFO] & 0b10000L) != 0 ? WHITE_KING : BLACK_KING], (board[INFO] & 0b10000L)) == board[(board[INFO] & 0b10000L) != 0 ? WHITE_KING : BLACK_KING]){
                return (MAX_ITER - iter + 1) * ((board[INFO] & 0b10000L) == our_side ? -CHECK_MATE_SCORE : CHECK_MATE_SCORE);
            } else return 0;
        }
        
        sort_deltas(deltas, not_null_index, iter%2 == 0);

        //maximizing
        if (iter%2 == 0){
            int best_eval = Integer.MIN_VALUE;
            for (DeltaMove move : deltas){
                if (move == null){
                    break;
                }
                if (iter < MAX_ITER){
                    move.swap(board);
                    move.eval = search(board, iter + 1, alpha, beta);
                    move.swap(board);
                } 
                
                best_eval = Math.max(best_eval, move.eval);
                alpha = Math.max(best_eval,alpha);
                if (beta <= alpha || move.eval >= CHECK_MATE_SCORE) break;
            }
            return best_eval; 
        } 
        //minimizing
        else {
            int best_eval = Integer.MAX_VALUE;
            for (DeltaMove move : deltas){
                if (move == null){
                    break;
                }
                if (iter < MAX_ITER){
                    move.swap(board);
                    move.eval = search(board, iter + 1, alpha, beta);
                    move.swap(board);
                } 

                best_eval = Math.min(best_eval, move.eval);
                beta = Math.min(best_eval,beta);
                if (beta <= alpha || move.eval <= -CHECK_MATE_SCORE) break;
            }
            return best_eval;   
        }  
        
        
    }

    static void sort_deltas(DeltaMove[] delta_moves, boolean maximizing){
        Arrays.sort(delta_moves, (DeltaMove a, DeltaMove b) -> {
            if (a == null){
                if (b == null){
                    return 0;
                } else {
                    return 1;
                }
            } else if (b == null){
                return -1;
            } else {
                return maximizing ? Integer.compare(b.eval,a.eval) : Integer.compare(a.eval,b.eval);
            }
        });
    }

    static DeltaMove[] filter_deltas(DeltaMove[] delta_moves, int last_max_iter, int iter){
        sort_deltas(delta_moves, true);

        if (last_max_iter < iter){
            return delta_moves;
        }
        int cut_off_length = (delta_moves.length - MIN_MOVES_TO_SEARCH) / (iter - last_max_iter - 1) + delta_moves.length;

        return Arrays.copyOf(delta_moves,cut_off_length);
    }

    static void sort_deltas(DeltaMove[] delta_moves, int sorting_cap, boolean maximizing){
        Arrays.sort(delta_moves, 0, sorting_cap, (DeltaMove a, DeltaMove b) -> {
            return maximizing ? Integer.compare(b.eval,a.eval) : Integer.compare(a.eval,b.eval);
        });
    }

    static long is_sq_in_check(long[] board, long sq, long side_attacked){
        if (side_attacked == 0){
            long our_pieces = sq | board[BLACK_QUEEN] | board[BLACK_ROOK] | board[BLACK_BISHOP] | board[BLACK_KNIGHT] | board[BLACK_PAWN];
            long opposing_pieces = board[WHITE_KING] | board[WHITE_QUEEN] | board[WHITE_ROOK] | board[WHITE_BISHOP] | board[WHITE_KNIGHT] | board[WHITE_PAWN];
            long pieces = our_pieces | opposing_pieces;

            long pos = sq << 8;
            while((pos & (pieces | Consts.RANK_1)) == 0 && pos != 0){
                pos <<= 8;
            }
            if ((pos & Consts.RANK_1) == 0 && ((pos & board[WHITE_ROOK]) != 0 || (pos & board[WHITE_QUEEN]) != 0 || ((pos & board[WHITE_KING]) != 0 && pos == sq << 8))){
                return sq;
            }
            pos = sq >>> 8;
            while((pos & pieces) == 0 && pos != 0){
                pos >>>= 8;
            }
            if (((pos & board[WHITE_ROOK]) != 0 || (pos & board[WHITE_QUEEN]) != 0 || ((pos & board[WHITE_KING]) != 0 && pos == sq >>> 8))){
                return sq;
            }
            pos = sq << 1;
            while((pos & (pieces | Consts.FILE_H)) == 0 && pos != 0){
                pos <<= 1;
            }
            if ((pos & Consts.FILE_H) == 0 && ((pos & board[WHITE_ROOK]) != 0 || (pos & board[WHITE_QUEEN]) != 0 || ((pos & board[WHITE_KING]) != 0 && pos == sq << 1))){
                return sq;
            }
            pos = sq >>> 1;
            while((pos & (pieces | Consts.FILE_A)) == 0 && pos != 0){
                pos >>= 1;
            }
            if ((pos & Consts.FILE_A) == 0 && ((pos & board[WHITE_ROOK]) != 0 || (pos & board[WHITE_QUEEN]) != 0 || ((pos & board[WHITE_KING]) != 0 && pos == sq >>> 1))){
                return sq;
            }

            pos = sq << 7;
            while((pos & (pieces | Consts.RANK_1 | Consts.FILE_A)) == 0 && pos != 0){
                pos <<= 7;
            }
            if ((pos & (Consts.RANK_1 | Consts.FILE_A)) == 0 && pos != 0 && ((pos & board[WHITE_BISHOP]) != 0 || (pos & board[WHITE_QUEEN]) != 0 || ((pos & board[WHITE_KING]) != 0 && pos == sq << 7))){
                return sq;
            }

            pos = sq << 9;
            while((pos & (pieces | Consts.FILE_H | Consts.RANK_1)) == 0 && pos != 0){
                pos <<= 9;
            }
            if ((pos & (Consts.FILE_H | Consts.RANK_1)) == 0 && ((pos & board[WHITE_BISHOP]) != 0 || (pos & board[WHITE_QUEEN]) != 0 || ((pos & board[WHITE_KING]) != 0 && pos == sq << 9))){
                return sq;
            }

            pos = sq >>> 7;
            while((pos & (pieces | Consts.FILE_H)) == 0 && pos != 0){
                pos >>>= 7;
            }
            if ((pos & Consts.FILE_H) == 0 && ((pos & board[WHITE_BISHOP]) != 0 || (pos & board[WHITE_QUEEN]) != 0 || (pos == sq >>> 7 && ((pos & board[WHITE_KING]) != 0 || (pos & board[WHITE_PAWN]) != 0)))){
                return sq;
            }

            pos = sq >>> 9;
            while((pos & (pieces | Consts.FILE_A)) == 0 && pos != 0){
                pos >>>= 9;
            }
            if ((pos & Consts.FILE_A) == 0 && ((pos & board[WHITE_BISHOP]) != 0 || (pos & board[WHITE_QUEEN]) != 0 || (pos == sq >>> 9 && ((pos & board[WHITE_KING]) != 0 || (pos & board[WHITE_PAWN]) != 0)))){
                return sq;
            }

            if((((sq & ~(Consts.RANK_8 | Consts.RANK_7 | Consts.FILE_A)) << 17) & board[WHITE_KNIGHT]) != 0) return sq;
            if((((sq & ~(Consts.RANK_8 | Consts.RANK_7 | Consts.FILE_H)) << 15) & board[WHITE_KNIGHT]) != 0) return sq;
            if((((sq & ~(Consts.RANK_8 | Consts.FILE_A | Consts.FILE_B)) << 10) & board[WHITE_KNIGHT]) != 0) return sq;
            if((((sq & ~(Consts.RANK_8 | Consts.FILE_G | Consts.FILE_H)) << 6) & board[WHITE_KNIGHT]) != 0) return sq;
            if((((sq & ~(Consts.RANK_2 | Consts.RANK_1 | Consts.FILE_H)) >>> 17) & board[WHITE_KNIGHT]) != 0) return sq;
            if((((sq & ~(Consts.RANK_2 | Consts.RANK_1 | Consts.FILE_A)) >>> 15) & board[WHITE_KNIGHT]) != 0) return sq;
            if((((sq & ~(Consts.RANK_1 | Consts.FILE_H | Consts.FILE_G)) >>> 10) & board[WHITE_KNIGHT]) != 0) return sq;
            if((((sq & ~(Consts.RANK_1 | Consts.FILE_A | Consts.FILE_B)) >>> 6) & board[WHITE_KNIGHT]) != 0) return sq;

            return 0;
        } else {
            long our_pieces = board[WHITE_KING] | board[WHITE_QUEEN] | board[WHITE_ROOK] | board[WHITE_BISHOP] | board[WHITE_KNIGHT] | board[WHITE_PAWN];
            long opposing_pieces = board[BLACK_KING] | board[BLACK_QUEEN] | board[BLACK_ROOK] | board[BLACK_BISHOP] | board[BLACK_KNIGHT] | board[BLACK_PAWN];
            long pieces = our_pieces | opposing_pieces;

            long pos = sq << 8;
            while((pos & (pieces | Consts.RANK_1)) == 0 && pos != 0){
                pos <<= 8;
            }
            if ((pos & Consts.RANK_1) == 0 && ((pos & board[BLACK_ROOK]) != 0 || (pos & board[BLACK_QUEEN]) != 0 || ((pos & board[BLACK_KING]) != 0 && pos == sq << 8))){
                return sq;
            }

            pos = sq >>> 8;
            while((pos & pieces) == 0 && pos != 0){
                pos >>>= 8;
            }
            if (pos != 0 && ((pos & board[BLACK_ROOK]) != 0 || (pos & board[BLACK_QUEEN]) != 0 || ((pos & board[BLACK_KING]) != 0 && pos == sq >>> 8))){
                return sq;
            }

            pos = sq << 1;
            
            while((pos & (pieces | Consts.FILE_H)) == 0 && pos != 0){
                pos <<= 1;
            }
            if ((pos & Consts.FILE_H) == 0 && ((pos & board[BLACK_ROOK]) != 0 || (pos & board[BLACK_QUEEN]) != 0 || ((pos & board[BLACK_KING]) != 0 && pos == sq << 1))){
                return sq;
            }

            pos = sq >>> 1L;
            while((pos & (pieces | Consts.FILE_A)) == 0 && pos != 0){
                pos >>>= 1L;
            }
            if ((pos & Consts.FILE_A) == 0 && pos != 0 && ((pos & board[BLACK_ROOK]) != 0 || (pos & board[BLACK_QUEEN]) != 0 || ((pos & board[BLACK_KING]) != 0 && pos == sq >>> 1))){
                return sq;
            }

            pos = sq << 7;
            while((pos & (pieces | Consts.RANK_1 | Consts.FILE_A)) == 0 && pos != 0){
                pos <<= 7;
            }
            if ((pos & (Consts.RANK_1 | Consts.FILE_A)) == 0 && pos != 0 &&((pos & board[BLACK_BISHOP]) != 0 || (pos & board[BLACK_QUEEN]) != 0 || (pos == sq << 7 && ((pos & board[BLACK_KING]) != 0 || (pos & board[BLACK_PAWN]) != 0)))){
                return sq;
            }

            pos = sq << 9;
            while((pos & (pieces | Consts.FILE_H | Consts.RANK_1)) == 0 && pos != 0){
                pos <<= 9;
            }
            if ((pos & (Consts.FILE_H | Consts.RANK_1)) == 0 && pos != 0 && ((pos & board[BLACK_BISHOP]) != 0 || (pos & board[BLACK_QUEEN]) != 0 || (pos == sq << 9 && ((pos & board[BLACK_KING]) != 0 || (pos & board[BLACK_PAWN]) != 0)))){
                return sq;
            }

            pos = sq >>> 7;
            while((pos & (pieces | Consts.FILE_H)) == 0 && pos != 0){
                pos >>>= 7;
            }
            if ((pos & Consts.FILE_H) == 0 && pos != 0 && ((pos & board[BLACK_BISHOP]) != 0 || (pos & board[BLACK_QUEEN]) != 0 || ((pos & board[BLACK_KING]) != 0 && pos == sq >>> 7))){
                return sq;
            }

            pos = sq >>> 9;
            while((pos & (pieces | Consts.FILE_A)) == 0 && pos != 0 && pos != 0){
                pos >>>= 9;
            }
            if ((pos & Consts.FILE_A) == 0 && pos != 0 && ((pos & board[BLACK_BISHOP]) != 0 || (pos & board[BLACK_QUEEN]) != 0 || ((pos & board[BLACK_KING]) != 0 && pos == sq >>> 9))){
                return sq;
            }

            if((((sq & ~(Consts.RANK_8 | Consts.RANK_7 | Consts.FILE_A)) << 17) & board[BLACK_KNIGHT]) != 0) return sq;
            if((((sq & ~(Consts.RANK_8 | Consts.RANK_7 | Consts.FILE_H)) << 15) & board[BLACK_KNIGHT]) != 0) return sq;
            if((((sq & ~(Consts.RANK_8 | Consts.FILE_A | Consts.FILE_B)) << 10) & board[BLACK_KNIGHT]) != 0) return sq;
            if((((sq & ~(Consts.RANK_8 | Consts.FILE_G | Consts.FILE_H)) << 6) & board[BLACK_KNIGHT]) != 0) return sq;
            if((((sq & ~(Consts.RANK_2 | Consts.RANK_1 | Consts.FILE_H)) >>> 17) & board[BLACK_KNIGHT]) != 0) return sq;
            if((((sq & ~(Consts.RANK_2 | Consts.RANK_1 | Consts.FILE_A)) >>> 15) & board[BLACK_KNIGHT]) != 0) return sq;
            if((((sq & ~(Consts.RANK_1 | Consts.FILE_H | Consts.FILE_G)) >>> 10) & board[BLACK_KNIGHT]) != 0) return sq;
            if((((sq & ~(Consts.RANK_1 | Consts.FILE_A | Consts.FILE_B)) >>> 6) & board[BLACK_KNIGHT]) != 0) return sq;

            return 0;
        }
    }
    
    static long[] get_moves(long[] board){
        long[] moves = new long[move_array_size];
        int index = 1;

        // white plays
        if ((board[INFO] & 0b10000L) != 0){
            
            long our_pieces = board[WHITE_KING] | board[WHITE_QUEEN] | board[WHITE_ROOK] | board[WHITE_BISHOP] | board[WHITE_KNIGHT] | board[WHITE_PAWN];
            long opposing_pieces = board[BLACK_KING] | board[BLACK_QUEEN] | board[BLACK_ROOK] | board[BLACK_BISHOP] | board[BLACK_KNIGHT] | board[BLACK_PAWN];

            moves[index++] = Consts.HEADER;
            index = white_king_moves(board[WHITE_KING], our_pieces, opposing_pieces, board, moves, index);
            
            moves[index++] = Consts.HEADER;
            index = white_pawn_moves(board[WHITE_PAWN], our_pieces, opposing_pieces, board[EN_PASS], moves, index);

            moves[index++] = Consts.HEADER;
            long this_board = board[WHITE_ROOK];
            long n;
            while(this_board != 0){
                n = this_board & -this_board;
                index = rook_moves(n,our_pieces,opposing_pieces,moves,index);
                this_board &= this_board - 1L;
            }

            moves[index++] = Consts.HEADER;
            this_board = board[WHITE_BISHOP];
            while(this_board != 0){
                n = this_board & -this_board;
                index = bishop_moves(n,our_pieces,opposing_pieces,moves,index);
                this_board &= this_board - 1L;
            }

            moves[index++] = Consts.HEADER;
            this_board = board[WHITE_KNIGHT];
            while(this_board != 0){
                n = this_board & -this_board;
                index = knight_moves(n,our_pieces,moves,index);
                this_board &= this_board - 1L;
            }

            moves[index++] = Consts.HEADER;
            this_board = board[WHITE_QUEEN];
            while(this_board != 0){
                n = this_board & -this_board;
                index = queen_moves(n,our_pieces,opposing_pieces,moves,index);
                this_board &= this_board - 1L;
            }

        } else {
            // black plays
            long opposing_pieces = board[WHITE_KING] | board[WHITE_QUEEN] | board[WHITE_ROOK] | board[WHITE_BISHOP] | board[WHITE_KNIGHT] | board[WHITE_PAWN];
            long our_pieces = board[BLACK_KING] | board[BLACK_QUEEN] | board[BLACK_ROOK] | board[BLACK_BISHOP] | board[BLACK_KNIGHT] | board[BLACK_PAWN];

            moves[index++] = Consts.HEADER;
            index = black_king_moves(board[BLACK_KING], our_pieces, opposing_pieces, board, moves, index);
            
            moves[index++] = Consts.HEADER;
            index = black_pawn_moves(board[BLACK_PAWN], our_pieces, opposing_pieces, board[EN_PASS], moves, index);
            
            moves[index++] = Consts.HEADER;
            long this_board = board[BLACK_ROOK];
            long n;
            while(this_board != 0){
                n = this_board & -this_board;
                index = rook_moves(n,our_pieces,opposing_pieces,moves,index);
                this_board &= this_board - 1L;
            }

            moves[index++] = Consts.HEADER;
            this_board = board[BLACK_BISHOP];
            while(this_board != 0){
                n = this_board & -this_board;
                index = bishop_moves(n,our_pieces,opposing_pieces,moves,index);
                this_board &= this_board - 1L;
            }

            moves[index++] = Consts.HEADER;
            this_board = board[BLACK_KNIGHT];
            while(this_board != 0){
                n = this_board & -this_board;
                index = knight_moves(n,our_pieces,moves,index);
                this_board &= this_board - 1L;
            }

            moves[index++] = Consts.HEADER;
            this_board = board[BLACK_QUEEN];
            while(this_board != 0){
                n = this_board & -this_board;
                index = queen_moves(n,our_pieces,opposing_pieces,moves,index);
                this_board &= this_board - 1L;
            }
        }
        moves[0] = index;
        moves[index++] = Consts.RANK_1;
        return moves;
    }

    static int white_pawn_moves(long pawns, long our_pieces, long opposing_pieces, long en_pass, long[] moves, int index){

        long push1 = (pawns << 8) & ~(our_pieces | opposing_pieces);
        long n = (push1 & ~(push1 - 1L));
        while(n != 0){
            moves[index++] = n | (n >>> 8);
            push1 &= push1 - 1L;
            n = (push1 & ~(push1 - 1L));
        }

        long push2 = ((pawns & Consts.RANK_2) << 16) & ~(our_pieces | opposing_pieces | our_pieces << 8 | opposing_pieces << 8);
        n = (push2 & ~(push2 - 1L));
        while(n != 0){
            moves[index++] = n | (n >>> 16);
            push2 &= push2 - 1L;
            n = (push2 & ~(push2 - 1L));
        }

        long takel = ((pawns & ~Consts.FILE_A) << 9) & (opposing_pieces | en_pass);
        n = (takel & ~(takel - 1L));
        while(n != 0){
            moves[index++] = n | (n >>> 9);
            takel &= takel - 1L;
            n = (takel & ~(takel - 1L));
        }

        long taker = ((pawns & ~Consts.FILE_H) << 7) & (opposing_pieces | en_pass);
        n = (taker & ~(taker - 1L));
        while(n != 0){
            moves[index++] = n | (n >>> 7);
            taker &= taker - 1L;
            n = (taker & ~(taker - 1L));
        }
        
        return index;
    }

    static int black_pawn_moves(long pawns, long our_pieces, long opposing_pieces, long en_pass, long[] moves, int index){

        long push1 = (pawns >>> 8) & ~(our_pieces | opposing_pieces);
        long n = (push1 & ~(push1 - 1L));
        while(n != 0){
            moves[index++] = n | (n << 8);
            push1 &= push1 - 1L;
            n = (push1 & ~(push1 - 1L));
        }

        long push2 = ((pawns & Consts.RANK_7) >>> 16) & ~(our_pieces | opposing_pieces | our_pieces >>> 8 | opposing_pieces >>> 8);
        n = (push2 & ~(push2 - 1L));
        while(n != 0){
            moves[index++] = n | (n << 16);
            push2 &= push2 - 1L;
            n = (push2 & ~(push2 - 1L));
        }

        long takel = ((pawns & ~Consts.FILE_A) >>> 7) & (opposing_pieces | en_pass);
        n = (takel & ~(takel - 1L));
        while(n != 0){
            moves[index++] = n | (n << 7);
            takel &= takel - 1L;
            n = (takel & ~(takel - 1L));
        }

        long taker = ((pawns & ~Consts.FILE_H) >>> 9) & (opposing_pieces | en_pass);
        n = (taker & ~(taker - 1L));
        while(n != 0){
            moves[index++] = n | (n << 9);
            taker &= taker - 1L;
            n = (taker & ~(taker - 1L));
        }
        
        return index;
    }

    static int rook_moves(long old_pos, long our_pieces, long opposing_pieces, long[] moves, int index){
        long pieces = our_pieces | opposing_pieces;

        long pos = old_pos << 8;
        while((pos & (pieces | Consts.RANK_1)) == 0 && pos != 0){
            moves[index++] = old_pos | pos;
            pos <<= 8;
        }
        if ((pos & opposing_pieces) != 0 && (pos & Consts.RANK_1) == 0){
            moves[index++] = old_pos | pos;
        }
        pos = old_pos >>> 8;
        while((pos & pieces) == 0 && pos != 0 && pos != 0){
            moves[index++] = old_pos | pos;
            pos >>>= 8;
        }
        if ((pos & opposing_pieces) != 0 && pos != 0){
            moves[index++] = old_pos | pos;
        }
        pos = old_pos << 1;
        while((pos & (pieces | Consts.FILE_H)) == 0 && pos != 0){
            moves[index++] = old_pos | pos;
            pos <<= 1;
        }
        if ((pos & opposing_pieces) != 0 && (pos & Consts.FILE_H) == 0){
            moves[index++] = old_pos | pos;
        }
        pos = old_pos >>> 1;
        while((pos & (pieces | Consts.FILE_A)) == 0 && pos != 0 && pos != 0){
            moves[index++] = old_pos | pos;
            pos >>= 1;
        }
        if ((pos & opposing_pieces) != 0 && (pos & Consts.FILE_A) == 0){
            moves[index++] = old_pos | pos;
        }
        return index;
    }

    static int bishop_moves(long old_pos, long our_pieces, long opposing_pieces, long[] moves, int index){
        long pieces = our_pieces | opposing_pieces;
 
        long pos = old_pos << 7;
        while((pos & (pieces | Consts.RANK_1 | Consts.FILE_A)) == 0 && pos != 0){
            moves[index++] = old_pos | pos;
            pos <<= 7;
        }
        if ((pos & opposing_pieces) != 0 && (pos & (Consts.RANK_1 | Consts.FILE_A)) == 0){
            moves[index++] = old_pos | pos;
        }

        pos = old_pos << 9;
        while((pos & (pieces | Consts.FILE_H | Consts.RANK_1)) == 0 && pos != 0){
            moves[index++] = old_pos | pos;
            pos <<= 9;
        }
        if ((pos & opposing_pieces) != 0 && (pos & (Consts.FILE_H | Consts.RANK_1)) == 0){
            moves[index++] = old_pos | pos;
        }

        pos = old_pos >>> 7;
        while((pos & (pieces | Consts.FILE_H)) == 0 && pos != 0){
            moves[index++] = old_pos | pos;
            pos >>>= 7;
        }
        if ((pos & opposing_pieces) != 0 && (pos & Consts.FILE_H) == 0){
            moves[index++] = old_pos | pos;
        }

        pos = old_pos >>> 9;
        while((pos & (pieces | Consts.FILE_A)) == 0 && pos != 0){
            moves[index++] = old_pos | pos;
            pos >>= 9;
        }
        if ((pos & opposing_pieces) != 0 && (pos & Consts.FILE_A) == 0){
            moves[index++] = old_pos | pos;
        }

        return index;
    }

    static int queen_moves(long old_pos, long our_pieces, long opposing_pieces, long[] moves, int index){
        long pieces = our_pieces | opposing_pieces;

        long pos = old_pos << 8;
        while((pos & (pieces | Consts.RANK_1)) == 0 && pos != 0){
            moves[index++] = old_pos | pos;
            pos <<= 8;
        }
        if ((pos & opposing_pieces) != 0 && (pos & Consts.RANK_1) == 0){
            moves[index++] = old_pos | pos;
        }
        pos = old_pos >>> 8;
        while((pos & pieces) == 0 && pos != 0){
            moves[index++] = old_pos | pos;
            pos >>>= 8;
        }
        if ((pos & opposing_pieces) != 0){
            moves[index++] = old_pos | pos;
        }
        pos = old_pos << 1;
        while((pos & (pieces | Consts.FILE_H)) == 0 && pos != 0){
            moves[index++] = old_pos | pos;
            pos <<= 1;
        }
        if ((pos & opposing_pieces) != 0 && (pos & Consts.FILE_H) == 0){
            moves[index++] = old_pos | pos;
        }
        pos = old_pos >>> 1;
        while((pos & (pieces | Consts.FILE_A)) == 0 && pos != 0){
            moves[index++] = old_pos | pos;
            pos >>= 1;
        }
        if ((pos & opposing_pieces) != 0 && (pos & Consts.FILE_A) == 0){
            moves[index++] = old_pos | pos;
        }

        pos = old_pos << 7;
        while((pos & (pieces | Consts.RANK_1 | Consts.FILE_A)) == 0 && pos != 0){
            moves[index++] = old_pos | pos;
            pos <<= 7;
        }
        if ((pos & opposing_pieces) != 0 && (pos & (Consts.RANK_1 | Consts.FILE_A)) == 0){
            moves[index++] = old_pos | pos;
        }

        pos = old_pos << 9;
        while((pos & (pieces | Consts.FILE_H | Consts.RANK_1)) == 0 && pos != 0){
            moves[index++] = old_pos | pos;
            pos <<= 9;
        }
        if ((pos & opposing_pieces) != 0 && (pos & (Consts.FILE_H | Consts.RANK_1)) == 0){
            moves[index++] = old_pos | pos;
        }

        pos = old_pos >>> 7;
        while((pos & (pieces | Consts.FILE_H)) == 0 && pos != 0 && pos != 0){
            moves[index++] = old_pos | pos;
            pos >>>= 7;
        }
        if ((pos & opposing_pieces) != 0 && (pos & Consts.FILE_H) == 0){
            moves[index++] = old_pos | pos;
        }

        pos = old_pos >>> 9;
        while((pos & (pieces | Consts.FILE_A)) == 0 && pos != 0 && pos != 0){
            moves[index++] = old_pos | pos;
            pos >>= 9;
        }
        if ((pos & opposing_pieces) != 0 && (pos & Consts.FILE_A) == 0){
            moves[index++] = old_pos | pos;
        }

        return index;
    }

    static int white_king_moves(long old_pos, long our_pieces, long opposing_pieces, long[] board, long[] moves, int index){
        
        moves[index++] = (old_pos & ~(Consts.RANK_1)) | ((old_pos >>> 8) & ~our_pieces);
        moves[index++] = (old_pos & ~(Consts.RANK_1 | Consts.FILE_A)) | ((old_pos >>> 7) & ~our_pieces);
        moves[index++] = (old_pos & ~(Consts.FILE_A)) | ((old_pos << 1) & ~our_pieces);
        moves[index++] = (old_pos & ~(Consts.RANK_8 | Consts.FILE_A)) | ((old_pos << 9) & ~our_pieces);
        moves[index++] = (old_pos & ~(Consts.RANK_8)) | ((old_pos << 8) & ~our_pieces);
        moves[index++] = (old_pos & ~(Consts.RANK_8 | Consts.FILE_H)) | ((old_pos << 7) & ~our_pieces);
        moves[index++] = (old_pos & ~(Consts.FILE_H)) | ((old_pos >>> 1) & ~our_pieces);
        moves[index++] = (old_pos & ~(Consts.RANK_1 | Consts.FILE_H)) | ((old_pos >>> 9) & ~our_pieces);

        long pieces = our_pieces | opposing_pieces;
        if ((board[INFO] & 0b1000L) != 0 && 
        (pieces & Consts.WHITE_KINGSIDE_SPACE) == 0 &&
        (board[WHITE_ROOK] & 0b10000000L) != 0 &&
        is_sq_in_check(board, 0b00001000L,1) == 0 && 
        is_sq_in_check(board, 0b00000100L,1) == 0 && 
        is_sq_in_check(board, 0b00000010L,1) == 0 && 
        is_sq_in_check(board, 0b00000001L,1) == 0) moves[index++] = 0b1010L;
    
        if ((board[INFO] & 0b0100L) != 0 && 
        (pieces & Consts.WHITE_QUEENSIDE_SPACE) == 0 &&
        (board[BLACK_ROOK] & 0b1L) != 0 &&
        is_sq_in_check(board, 0b10000000L,1) == 0 && 
        is_sq_in_check(board, 0b00100000L,1) == 0 && 
        is_sq_in_check(board, 0b00010000L,1) == 0 && 
        is_sq_in_check(board, 0b00001000L,1) == 0) moves[index++] = 0b00101000L;
        
        return index;
    }

    static int black_king_moves(long old_pos, long our_pieces, long opposing_pieces, long[] board, long[] moves, int index){
        
        moves[index++] = (old_pos & ~(Consts.RANK_1)) | ((old_pos >>> 8) & ~our_pieces);
        moves[index++] = (old_pos & ~(Consts.RANK_1 | Consts.FILE_A)) | ((old_pos >>> 7) & ~our_pieces);
        moves[index++] = (old_pos & ~(Consts.FILE_A)) | ((old_pos << 1) & ~our_pieces);
        moves[index++] = (old_pos & ~(Consts.RANK_8 | Consts.FILE_A)) | ((old_pos << 9) & ~our_pieces);
        moves[index++] = (old_pos & ~(Consts.RANK_8)) | ((old_pos << 8) & ~our_pieces);
        moves[index++] = (old_pos & ~(Consts.RANK_8 | Consts.FILE_H)) | ((old_pos << 7) & ~our_pieces);
        moves[index++] = (old_pos & ~(Consts.FILE_H)) | ((old_pos >>> 1) & ~our_pieces);
        moves[index++] = (old_pos & ~(Consts.RANK_1 | Consts.FILE_H)) | ((old_pos >>> 9) & ~our_pieces);

        long pieces = our_pieces | opposing_pieces;
        if ((board[INFO] & 0b0010L) != 0 && 
        (pieces & Consts.BLACK_KINGSIDE_SPACE) == 0 && 
        (board[BLACK_ROOK] & (0b10000000L << 56))  != 0 &&
        is_sq_in_check(board, 0b00001000L << 56,0) == 0 && 
        is_sq_in_check(board, 0b00000100L << 56,0) == 0 && 
        is_sq_in_check(board, 0b00000010L << 56,0) == 0 && 
        is_sq_in_check(board, 0b00000001L << 56,0) == 0) {
            moves[index++] = 0b00001010L << 56;
        }
    
        if ((board[INFO] & 0b0001L) != 0 && 
        ((pieces & Consts.BLACK_QUEENSIDE_SPACE) == 0 &&
        (board[BLACK_ROOK] & (0b1L << 56))  != 0 &&
        is_sq_in_check(board, 0b10000000L << 56,0) == 0 && 
        is_sq_in_check(board, 0b00100000L << 56,0) == 0 && 
        is_sq_in_check(board, 0b00010000L << 56,0) == 0 && 
        is_sq_in_check(board, 0b00001000L << 56,0) == 0)) {
            moves[index++] = 0b00101000L << 56;
        }
        
        return index;
    }

    static int knight_moves(long old_pos, long our_pieces, long[] moves, int index){
        
        moves[index++] = (old_pos & ~(Consts.RANK_8 | Consts.RANK_7 | Consts.FILE_A)) | ((old_pos << 17) & ~our_pieces);
        moves[index++] = (old_pos & ~(Consts.RANK_8 | Consts.RANK_7 | Consts.FILE_H)) | ((old_pos << 15) & ~our_pieces);
        moves[index++] = (old_pos & ~(Consts.RANK_8 | Consts.FILE_A | Consts.FILE_B)) | ((old_pos << 10) & ~our_pieces);
        moves[index++] = (old_pos & ~(Consts.RANK_8 | Consts.FILE_G | Consts.FILE_H)) | ((old_pos << 6) & ~our_pieces);
        moves[index++] = (old_pos & ~(Consts.RANK_2 | Consts.RANK_1 | Consts.FILE_H)) | ((old_pos >>> 17) & ~our_pieces);
        moves[index++] = (old_pos & ~(Consts.RANK_2 | Consts.RANK_1 | Consts.FILE_A)) | ((old_pos >>> 15) & ~our_pieces);
        moves[index++] = (old_pos & ~(Consts.RANK_1 | Consts.FILE_H | Consts.FILE_G)) | ((old_pos >>> 10) & ~our_pieces);
        moves[index++] = (old_pos & ~(Consts.RANK_1 | Consts.FILE_A | Consts.FILE_B)) | ((old_pos >>> 6) & ~our_pieces);

        return index;
    }

    static int evaluate(long[] board){
        long hash = hash(board);
        

        Integer eval = eval_map.get(hash);
        if (eval != null) return eval;

        int eg_num = 0;

        eg_num += Long.bitCount(board[WHITE_KING]) * Consts.gamephase[WHITE_KING];
        eg_num += Long.bitCount(board[WHITE_BISHOP]) * Consts.gamephase[WHITE_BISHOP];
        eg_num += Long.bitCount(board[WHITE_KNIGHT]) * Consts.gamephase[WHITE_KNIGHT];
        eg_num += Long.bitCount(board[WHITE_ROOK]) * Consts.gamephase[WHITE_ROOK];
        eg_num += Long.bitCount(board[WHITE_PAWN]) * Consts.gamephase[WHITE_PAWN];
        eg_num += Long.bitCount(board[WHITE_QUEEN]) * Consts.gamephase[WHITE_QUEEN];
        eg_num += Long.bitCount(board[BLACK_KING]) * Consts.gamephase[BLACK_KING];
        eg_num += Long.bitCount(board[BLACK_BISHOP]) * Consts.gamephase[BLACK_BISHOP];
        eg_num += Long.bitCount(board[BLACK_KNIGHT]) * Consts.gamephase[BLACK_KNIGHT];
        eg_num += Long.bitCount(board[BLACK_ROOK]) * Consts.gamephase[BLACK_ROOK];
        eg_num += Long.bitCount(board[BLACK_PAWN]) * Consts.gamephase[BLACK_PAWN];
        eg_num += Long.bitCount(board[BLACK_QUEEN]) * Consts.gamephase[BLACK_QUEEN];

        if (eg_num >= 16) eval = (Chess_Bot.our_side != 0 ? 1 : -1) * middle_eval(board);
        else if (eg_num <= 10) eval = (Chess_Bot.our_side != 0 ? 1 : -1) * end_eval(board);
        else eval = (Chess_Bot.our_side != 0 ? 1 : -1) * mixed_eval(board,eg_num);

        eval_map.put(hash,eval);
        return eval;
    }

    static int end_eval(long[] board){
        int eg_eval = 0;
        int pos;
        long this_board;
        long n;

        this_board = board[WHITE_KING];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            eg_eval += Consts.eg_white_king_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[WHITE_BISHOP];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            eg_eval += Consts.eg_white_bishop_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[WHITE_KNIGHT];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            eg_eval += Consts.eg_white_knight_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[WHITE_ROOK];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            eg_eval += Consts.eg_white_rook_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[WHITE_QUEEN];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            eg_eval += Consts.eg_white_queen_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[WHITE_PAWN];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            eg_eval += Consts.eg_white_pawn_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[BLACK_KING];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            eg_eval -= Consts.eg_black_king_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[BLACK_BISHOP];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            eg_eval -= Consts.eg_black_bishop_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[BLACK_KNIGHT];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            eg_eval -= Consts.eg_black_knight_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[BLACK_ROOK];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            eg_eval -= Consts.eg_black_rook_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[BLACK_QUEEN];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            eg_eval -= Consts.eg_black_queen_table[pos];
            this_board &= this_board - 1L;
        }

        this_board = board[BLACK_PAWN];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            eg_eval -= Consts.eg_black_pawn_table[pos];
            
            this_board &= this_board - 1L;
        }

        return eg_eval;
    }

    static int middle_eval(long[] board){
        int mg_eval = 0;
        int pos;
        long this_board;
        long n;

        this_board = board[WHITE_KING];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            mg_eval += Consts.mg_white_king_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[WHITE_BISHOP];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            mg_eval += Consts.mg_white_bishop_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[WHITE_KNIGHT];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            mg_eval += Consts.mg_white_knight_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[WHITE_ROOK];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            mg_eval += Consts.mg_white_rook_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[WHITE_QUEEN];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            mg_eval += Consts.mg_white_queen_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[WHITE_PAWN];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            mg_eval += Consts.mg_white_pawn_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[BLACK_KING];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            mg_eval -= Consts.mg_black_king_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[BLACK_BISHOP];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            mg_eval -= Consts.mg_black_bishop_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[BLACK_KNIGHT];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            mg_eval -= Consts.mg_black_knight_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[BLACK_ROOK];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            mg_eval -= Consts.mg_black_rook_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[BLACK_QUEEN];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            mg_eval -= Consts.mg_black_queen_table[pos];
            this_board &= this_board - 1L;
        }

        this_board = board[BLACK_PAWN];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            mg_eval -= Consts.mg_black_pawn_table[pos];
            
            this_board &= this_board - 1L;
        }

        return mg_eval;
    }

    static int mixed_eval(long[] board, int eg_num){
        int eg_eval = 0;
        int mg_eval = 0;
        int pos;
        long this_board;
        long n;

        this_board = board[WHITE_KING];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            eg_eval += Consts.eg_white_king_table[pos];
            mg_eval += Consts.mg_white_king_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[WHITE_BISHOP];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            eg_eval += Consts.eg_white_bishop_table[pos];
            mg_eval += Consts.mg_white_bishop_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[WHITE_KNIGHT];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            eg_eval += Consts.eg_white_knight_table[pos];
            mg_eval += Consts.mg_white_knight_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[WHITE_ROOK];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            eg_eval += Consts.eg_white_rook_table[pos];
            mg_eval += Consts.mg_white_rook_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[WHITE_QUEEN];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            eg_eval += Consts.eg_white_queen_table[pos];
            mg_eval += Consts.mg_white_queen_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[WHITE_PAWN];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            eg_eval += Consts.eg_white_pawn_table[pos];
            mg_eval += Consts.mg_white_pawn_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[BLACK_KING];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            eg_eval -= Consts.eg_black_king_table[pos];
            mg_eval -= Consts.mg_black_king_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[BLACK_BISHOP];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            eg_eval -= Consts.eg_black_bishop_table[pos];
            mg_eval -= Consts.mg_black_bishop_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[BLACK_KNIGHT];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            eg_eval -= Consts.eg_black_knight_table[pos];
            mg_eval -= Consts.mg_black_knight_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[BLACK_ROOK];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            eg_eval -= Consts.eg_black_rook_table[pos];
            mg_eval -= Consts.mg_black_rook_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[BLACK_QUEEN];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            eg_eval -= Consts.eg_black_queen_table[pos];
            mg_eval -= Consts.mg_black_queen_table[pos];
            
            this_board &= this_board - 1L;
        }

        this_board = board[BLACK_PAWN];
        
        while(this_board != 0){
            n = this_board & -this_board;
            pos = Long.numberOfLeadingZeros(n);
            eg_eval -= Consts.eg_black_pawn_table[pos];
            mg_eval -= Consts.mg_black_pawn_table[pos];
            
            this_board &= this_board - 1L;
        }

        return (mg_eval-eg_eval) / (24) * (eg_num) + eg_eval;
    }

    static long hash(long[] board){
        long hash = 0;
        for (int i = 0; i < 14; i++){
            
            hash ^= board[i];
            hash *= 0x9e3779b97f4a7c15L;
            hash = (hash << 5) | (hash >>> 59);
        }
        return hash;
    }
}


class DeltaMove {
    public int primary;
    public int secondary;
    public int tertiary;
    public long primary_move;
    public long secondary_move;
    public long tertiary_move;
    public long info_XOR;
    public long enPass_XOR;
    public int eval;

    public DeltaMove(int eval){
        this.eval = eval;
    }

    public DeltaMove(long[] board, long move, int piece){
        //Chess_Bot.turn_counter++;
        this.primary = piece;
        this.primary_move = move;
        long old_info = board[Chess_Bot.INFO];
        long old_enpass = board[Chess_Bot.EN_PASS];
        this.enPass_XOR = old_enpass;
        board[Chess_Bot.EN_PASS] = 0;

        

        // updating castling rights
        if (piece == Chess_Bot.WHITE_ROOK){
            if ((move & 0b10000000L) != 0) board[Chess_Bot.INFO] &= ~0b0100L;
            else if ((move & 0b1L) != 0) board[Chess_Bot.INFO] &= ~0b1000L;
        } else if (piece == Chess_Bot.BLACK_ROOK){
            if ((move & 0b10000000L << 56) != 0) board[Chess_Bot.INFO] &= ~0b0001L;
            else if ((move & 0b1L << 56) != 0) board[Chess_Bot.INFO] &= ~0b0010L;
        }
        
        // castling
        else if (piece == Chess_Bot.WHITE_KING){
            board[Chess_Bot.INFO] &= ~0b1100L;
            if (move == 0b1010L){
                this.secondary = Chess_Bot.WHITE_ROOK;
                this.secondary_move = 0b0101L;
                board[Chess_Bot.WHITE_ROOK] ^= 0b0101L;
                board[Chess_Bot.WHITE_KING] ^= 0b1010L;
                board[Chess_Bot.INFO] ^= 0b10000L;
                this.info_XOR = board[Chess_Bot.INFO] ^ old_info;
                return;
            } else if (move == 0b00101000L){
                this.secondary = Chess_Bot.WHITE_ROOK;
                this.secondary_move = 0b10010000L;
                board[Chess_Bot.WHITE_ROOK] ^= 0b10010000L;
                board[Chess_Bot.WHITE_KING] ^= 0b00101000L;
                board[Chess_Bot.INFO] ^= 0b10000L;
                this.info_XOR = board[Chess_Bot.INFO] ^ old_info;
                return;
            }
        } else if (piece == Chess_Bot.BLACK_KING){
            board[Chess_Bot.INFO] &= ~0b0011L;
            if (move == 0b1010L << 56){
                this.secondary = Chess_Bot.BLACK_ROOK;
                this.secondary_move = 0b0101L << 56;
                board[Chess_Bot.BLACK_ROOK] ^= 0b0101L << 56;
                board[Chess_Bot.BLACK_KING] ^= 0b1010L << 56;
                board[Chess_Bot.INFO] ^= 0b10000L;
                this.info_XOR = board[Chess_Bot.INFO] ^ old_info;
                return;
            } else if (move == 0b00101000L << 56){
                this.secondary = Chess_Bot.BLACK_ROOK;
                this.secondary_move = 0b10010000L << 56;
                board[Chess_Bot.BLACK_ROOK] ^= 0b10010000L << 56;
                board[Chess_Bot.BLACK_KING] ^= 0b00101000L << 56;
                board[Chess_Bot.INFO] ^= 0b10000L;
                this.info_XOR = board[Chess_Bot.INFO] ^ old_info;
                return;
            }
        }

        // black takes
        if ((old_info & 0b10000L) == 0){
            for (int i = Chess_Bot.WHITE_PAWN; i <= Chess_Bot.WHITE_QUEEN; i++){
                if ((move & ~board[piece] & board[i]) != 0){
                    this.tertiary = i;
                    this.tertiary_move = move & ~board[piece] & board[i];
                    board[i] ^= this.tertiary_move;
                    break;
                }
            }
        // white takes
        } else {
            for (int i = Chess_Bot.BLACK_PAWN; i <= Chess_Bot.BLACK_QUEEN; i++){
                if ((move & ~board[piece] & board[i]) != 0){
                    this.tertiary = i;
                    this.tertiary_move = move & ~board[piece] & board[i];
                    board[i] ^= this.tertiary_move;
                    break;
                }
            }
        }

        if (piece == Chess_Bot.WHITE_PAWN){
            // set en_pass
            if ((move & (Consts.RANK_2 | Consts.RANK_4)) == move){
                this.enPass_XOR = ((move << 8) & Consts.RANK_3) ^ old_enpass;
                board[Chess_Bot.EN_PASS] = ((move << 8) & Consts.RANK_3);
                board[piece] ^= move;
            // use en_pass
            } else if ((move & old_enpass) != 0){
                this.secondary = Chess_Bot.BLACK_PAWN;
                this.secondary_move = old_enpass >>> 8;
                board[Chess_Bot.BLACK_PAWN] ^= old_enpass >>> 8; 
                board[piece] ^= move;
            // promote
            } else if ((move & (Consts.RANK_8 | Consts.RANK_7)) == move){
                board[Chess_Bot.WHITE_QUEEN] ^= (move & Consts.RANK_8);
                this.primary_move = (move & Consts.RANK_7);
                board[piece] ^= (move & Consts.RANK_7);
                this.secondary = Chess_Bot.WHITE_QUEEN;
                this.secondary_move = move & Consts.RANK_8; 
            } else {
                board[piece] ^= move;
            }
        } else if (piece == Chess_Bot.BLACK_PAWN){
            // set en_pass
            if ((move & (Consts.RANK_7 | Consts.RANK_5)) == move){
                this.enPass_XOR = ((move << 8) & Consts.RANK_6) ^ old_enpass;
                board[Chess_Bot.EN_PASS] = ((move << 8) & Consts.RANK_6);
                board[piece] ^= move;
            // use en_pass
            } else if ((move & old_enpass) != 0){
                this.secondary = Chess_Bot.WHITE_PAWN;
                this.secondary_move = old_enpass << 8;
                board[Chess_Bot.WHITE_PAWN] ^= old_enpass << 8; 
                board[piece] ^= move;
            // promote
            } else if ((move & (Consts.RANK_1 | Consts.RANK_2)) == move){
                board[Chess_Bot.BLACK_QUEEN] ^= (move & Consts.RANK_1);
                this.primary_move = (move & Consts.RANK_2);
                board[piece] ^= (move & Consts.RANK_2);
                this.secondary = Chess_Bot.BLACK_QUEEN;
                this.secondary_move = move & Consts.RANK_1; 
            } else {
                board[piece] ^= move;
            }
        } else {
           
            board[piece] ^= this.primary_move;
        }

        board[Chess_Bot.INFO] ^= 0b10000L;
        this.info_XOR = board[Chess_Bot.INFO] ^ old_info;
    }

    public void swap(long[] board){
        board[this.primary] ^= this.primary_move;
        board[this.secondary] ^= this.secondary_move;
        board[this.tertiary] ^= this.tertiary_move;
        board[Chess_Bot.INFO] ^= this.info_XOR;
        board[Chess_Bot.EN_PASS] ^= this.enPass_XOR;
        //Chess_Bot.turn_counter += turn_change;
    }
}


class Consts {
    public static final int[] mg_white_pawn_table = {
        0,   0,   0,   0,   0,   0,  0,   0,
       98, 134,  61,  95,  68, 126, 34, -11,
       -6,   7,  26,  31,  65,  56, 25, -20,
      -14,  13,   6,  21,  23,  12, 17, -23,
      -27,  -2,  -5,  12,  17,   6, 10, -25,
      -26,  -4,  -4, -10,   3,   3, 33, -12,
      -35,  -1, -20, -23, -15,  24, 38, -22,
        0,   0,   0,   0,   0,   0,  0,   0,
    };
  
    public static final int[] eg_white_pawn_table = {
        0,   0,   0,   0,   0,   0,   0,   0,
      178, 173, 158, 134, 147, 132, 165, 187,
       94, 100,  85,  67,  56,  53,  82,  84,
       32,  24,  13,   5,  -2,   4,  17,  17,
       13,   9,  -3,  -7,  -7,  -8,   3,  -1,
        4,   7,  -6,   1,   0,  -5,  -1,  -8,
       13,   8,   8,  10,  13,   0,   2,  -7,
        0,   0,   0,   0,   0,   0,   0,   0,
    };
  
    public static final int[] mg_white_knight_table = {
      -167, -89, -34, -49,  61, -97, -15, -107,
       -73, -41,  72,  36,  23,  62,   7,  -17,
       -47,  60,  37,  65,  84, 129,  73,   44,
        -9,  17,  19,  53,  37,  69,  18,   22,
       -13,   4,  16,  13,  28,  19,  21,   -8,
       -23,  -9,  12,  10,  19,  17,  25,  -16,
       -29, -53, -12,  -3,  -1,  18, -14,  -19,
      -105, -21, -58, -33, -17, -28, -19,  -23,
    };
  
    public static final int[] eg_white_knight_table = {
      -58, -38, -13, -28, -31, -27, -63, -99,
      -25,  -8, -25,  -2,  -9, -25, -24, -52,
      -24, -20,  10,   9,  -1,  -9, -19, -41,
      -17,   3,  22,  22,  22,  11,   8, -18,
      -18,  -6,  16,  25,  16,  17,   4, -18,
      -23,  -3,  -1,  15,  10,  -3, -20, -22,
      -42, -20, -10,  -5,  -2, -20, -23, -44,
      -29, -51, -23, -15, -22, -18, -50, -64,
    };

    public static final int[] mg_white_bishop_table = {
        -29,   4, -82, -37, -25, -42,   7,  -8,
        -26,  16, -18, -13,  30,  59,  18, -47,
        -16,  37,  43,  40,  35,  50,  37,  -2,
         -4,   5,  19,  50,  37,  37,   7,  -2,
         -6,  13,  13,  26,  34,  12,  10,   4,
          0,  15,  15,  15,  14,  27,  18,  10,
          4,  15,  16,   0,   7,  21,  33,   1,
        -33,  -3, -14, -21, -13, -12, -39, -21,
    };
  
    public static final int[] eg_white_bishop_table = {
        -14, -21, -11,  -8,  -7,  -9, -17, -24,
         -8,  -4,   7, -12,  -3, -13,  -4, -14,
          2,  -8,   0,  -1,  -2,   6,   0,   4,
         -3,   9,  12,   9,  14,  10,   3,   2,
         -6,   3,  13,  19,   7,  10,  -3,  -9,
        -12,  -3,   8,  10,  13,   3,  -7, -15,
        -14, -18,  -7,  -1,   4,  -9, -15, -27,
        -23,  -9, -23,  -5,  -9, -16,  -5, -17,
    };
    
    public static final int[] mg_white_rook_table = {
        32,  42,  32,  51,  63,   9,  31,  43,
        27,  32,  58,  62,  80,  67,  26,  44,
        -5,  19,  26,  36,  17,  45,  61,  16,
       -24, -11,   7,  26,  24,  35,  -8, -20,
       -36, -26, -12,  -1,   9,  -7,   6, -23,
       -45, -25, -16, -17,   3,   0,  -5, -33,
       -44, -16, -20,  -9,  -1,  11,  -6, -71,
       -19, -13,   1,  17,  16,   7, -37, -26,
    };
    
    public static final int[] eg_white_rook_table = {
        13,  10,  18,  15,  12,  12,   8,   5,
        11,  13,  13,  11,  -3,   3,   8,   3,
         7,   7,   7,   5,   4,  -3,  -5,  -3,
         4,   3,  13,   1,   2,   1,  -1,   2,
         3,   5,   8,   4,  -5,  -6,  -8, -11,
        -4,   0,  -5,  -1,  -7, -12,  -8, -16,
        -6,  -6,   0,   2,  -9,  -9, -11,  -3,
        -9,   2,   3,  -1,  -5, -13,   4, -20,
    };
    
    public static final int[] mg_white_queen_table = {
       -28,   0,  29,  12,  59,  44,  43,  45,
       -24, -39,  -5,   1, -16,  57,  28,  54,
       -13, -17,   7,   8,  29,  56,  47,  57,
       -27, -27, -16, -16,  -1,  17,  -2,   1,
        -9, -26,  -9, -10,  -2,  -4,   3,  -3,
       -14,   2, -11,  -2,  -5,  -2,  14,   5,
       -35,  -8,  11,   2,   8,  15,  -3,   1,
        -1, -18,  -9,  10, -15, -25, -31, -50,
    };
    
    public static final int[] eg_white_queen_table = {
        -9,  22,  22,  27,  27,  19,  10,  20,
       -17,  20,  32,  41,  58,  25,  30,   0,
       -20,   6,   9,  49,  47,  35,  19,   9,
         3,  22,  24,  45,  57,  40,  57,  36,
       -18,  28,  19,  47,  31,  34,  39,  23,
       -16, -27,  15,   6,   9,  17,  10,   5,
       -22, -23, -30, -16, -16, -23, -36, -32,
       -33, -28, -22, -43,  -5, -32, -20, -41,
    };

    public static final int[] mg_white_king_table = {
        -65,  23,  16, -15, -56, -34,   2,  13,
         29,  -1, -20,  -7,  -8,  -4, -38, -29,
         -9,  24,   2, -16, -20,   6,  22, -22,
        -17, -20, -12, -27, -30, -25, -14, -36,
        -49,  -1, -27, -39, -46, -44, -33, -51,
        -14, -14, -22, -46, -44, -30, -15, -27,
          1,   7,  -8, -64, -43, -16,   9,   8,
        -15,  36,  12, -54,   8, -28,  24,  14,
    }; 

    public static final int[] eg_white_king_table = {
        -74, -35, -18, -18, -11,  15,   4, -17,
        -12,  17,  14,  17,  17,  38,  23,  11,
         10,  17,  23,  15,  20,  45,  44,  13,
         -8,  22,  24,  27,  26,  33,  26,   3,
        -18,  -4,  21,  24,  27,  23,   9, -11,
        -19,  -3,  11,  21,  23,  16,   7,  -9,
        -27, -11,   4,  13,  14,   4,  -5, -17,
        -53, -34, -21, -11, -28, -14, -24, -43
    };

    public static final int[] mg_black_king_table = new int[64];
    public static final int[] mg_black_pawn_table = new int[64];
    public static final int[] mg_black_rook_table = new int[64];
    public static final int[] mg_black_bishop_table = new int[64];
    public static final int[] mg_black_knight_table = new int[64];
    public static final int[] mg_black_queen_table = new int[64];

    public static final int[] eg_black_king_table = new int[64];
    public static final int[] eg_black_pawn_table = new int[64];
    public static final int[] eg_black_rook_table = new int[64];
    public static final int[] eg_black_bishop_table = new int[64];
    public static final int[] eg_black_knight_table = new int[64];
    public static final int[] eg_black_queen_table = new int[64];
    
    public static final int[] mg_value = {0, 82, 477, 365, 337, 1025, 0, 82, 477, 365, 337, 1025};
    public static final int[] eg_value = {0, 94, 512, 297, 281, 936, 0, 94, 512, 297, 281, 936};

    public static final int[][] mg_tables = {mg_white_king_table, mg_white_pawn_table, mg_white_rook_table, mg_white_bishop_table, mg_white_knight_table, mg_white_queen_table, 
                                             mg_black_king_table, mg_black_pawn_table, mg_black_rook_table, mg_black_bishop_table, mg_black_knight_table, mg_black_queen_table,}; 

    public static final int[][] eg_tables = {eg_white_king_table, eg_white_pawn_table, eg_white_rook_table, eg_white_bishop_table, eg_white_knight_table, eg_white_queen_table, 
                                             eg_black_king_table, eg_black_pawn_table, eg_black_rook_table, eg_black_bishop_table, eg_black_knight_table, eg_black_queen_table,}; 

    static {
        for (int i = Chess_Bot.WHITE_KING; i <= Chess_Bot.WHITE_QUEEN; i++){
            for (int j = 0; j < 64; j++){
                mg_tables[i][j] += mg_value[i];
                eg_tables[i][j] += eg_value[i];
            }
        }
    }

    static {
        for (int i = Chess_Bot.BLACK_KING; i <= Chess_Bot.BLACK_QUEEN; i++){
            for (int j = 0; j < 64; j++){
                mg_tables[i][j] = mg_tables[i - 6][(7 - j / 8) * 8 + j%8];
                eg_tables[i][j] = eg_tables[i - 6][(7 - j / 8) * 8 + j%8];
            }
        }
    }

    public static final int[] gamephase = {0,0,2,1,1,4,0,0,2,1,1,4};

    public static final String[] PIECE_NAMES = {
        "White King",
        "White Pawn",
        "White Rook",
        "White Bishop",
        "White Knight",
        "White Queen",
        "Black King",
        "Black Pawn",
        "Black Rook",
        "Black Bishop",
        "Black Knight",
        "Black Queen",
        "En Pass",
        "Info"
    };

    public static final long FILE_H = 0x0101010101010101L;
    public static final long FILE_G = 0x0202020202020202L;
    public static final long FILE_F = 0x0404040404040404L;
    public static final long FILE_E = 0x0808080808080808L;
    public static final long FILE_D = 0x1010101010101010L;
    public static final long FILE_C = 0x2020202020202020L;
    public static final long FILE_B = 0x4040404040404040L;
    public static final long FILE_A = 0x8080808080808080L;

    public static final long RANK_1 = 0xFFL;
    public static final long RANK_2 = 0xFF00L;
    public static final long RANK_3 = 0xFF0000L;
    public static final long RANK_4 = 0xFF000000L;
    public static final long RANK_5 = 0xFF00000000L;
    public static final long RANK_6 = 0xFF0000000000L;
    public static final long RANK_7 = 0xFF000000000000L;
    public static final long RANK_8 = 0xFF00000000000000L;

    public static final long WHITE_KINGSIDE_SPACE = 0b110L;
    public static final long WHITE_QUEENSIDE_SPACE = 0b01110000L;
    public static final long BLACK_KINGSIDE_SPACE = 0b110L << 56;
    public static final long BLACK_QUEENSIDE_SPACE = 0b01110000L << 56;

    public static final long WHITE_KINGSIDE_ATTACKED = 0b1111L;
    public static final long WHITE_QUEENSIDE_ATTACKED = 0b10111000L;
    public static final long BLACK_KINGSIDE_ATTACKED = 0b1111L << 56;
    public static final long BLACK_QUEENSIDE_ATTACKED = 0b10111000L << 56;

    public static final long HEADER = 0xFF00000000000000L;

    public static final char[] loading = {'|','/','-','\\'};

    public static final Map<Long,long[]> openings = new HashMap<>();

    static {
        String line;
        try (BufferedReader br = new BufferedReader(new FileReader(Chess.directory + "/resources/myBook.csv"))) {
            while ((line = br.readLine()) != null) {
                String[] fields = line.split(",");
                
                long hash = Long.parseLong(fields[0]);
                long move1 = Long.parseLong(fields[1]);
                long move2 = Long.parseLong(fields[2]);
                
                long[] moves = {move1,move2};
                Consts.openings.put(hash,moves);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("CANNOT OPEN myBook.csv");
        }
    }
}