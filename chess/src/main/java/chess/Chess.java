package chess;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 *
 * @author celwel01
 */
public class Chess {
    static final String directory = System.getProperty("user.dir");

    static int canvas_size = 600;
    static int box_size;
    static MyCanvas main_canvas;
    static Map<Character, Image> piece_images = new HashMap<>();
    static Color dark_squares = Color.decode("#7E8D85");
    static Color light_squares = Color.decode("#F0F7F4");
    static Color[] option_colour = {Color.decode("#697770"),Color.decode("#DDE0E0")};
    static Color[] highlight = {Color.decode("#FAD4B3"),Color.decode("#F6B379")};
    static double[] seconds = new double[2];
    static double increment;
    static String beginning_FEN;
    static boolean use_clock = false;

    static board_state live_board;

    public static void main(String[] args) {
        get_starting_pos();
        
        box_size = canvas_size/8;
        
        live_board = read_FEN(beginning_FEN); 
        //Arrays.fill(live_board.can_castle,true);

        JFrame frame = new JFrame("Chess");
        main_canvas = new MyCanvas();
        main_canvas.setSize(canvas_size,canvas_size);
        frame.add(main_canvas);
        frame.pack();
        frame.setVisible(true);
        main_canvas.requestFocusInWindow();
        
        get_piece_images();

    }

    static void get_starting_pos(){
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("Enter FEN for a custom position or \"Start\" for starting position.");
            String position = scanner.nextLine();

            if (position.equalsIgnoreCase("Start")){
                beginning_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
            } else {
                beginning_FEN = position;
            }

            String clock = "";
            while (!clock.equals("y") && !clock.equals("n")){
                System.out.println("Use clock? (y/n)");
                clock = scanner.nextLine();
            } 

            if (clock.equalsIgnoreCase("y")){
                use_clock = true;
                System.out.println("Enter number of minutes.");
                String mins = scanner.nextLine();
                System.out.println("Enter increment in seconds.");
                String incre = scanner.nextLine();

                seconds[0] = Double.parseDouble(mins) * 60;
                seconds[1] = Double.parseDouble(mins) * 60;

                increment = Double.parseDouble(incre);
            } else {
                System.out.println("Enter Chess Bot Timeout in seconds");
                String secs = scanner.nextLine();
                seconds[1] = Double.parseDouble(secs);
            }

            String first = "";
            while (!first.equals("b") && !first.equals("w") && !first.equals("r")){
                System.out.println("You play white (w), black (b), or random (r)?");
                first = scanner.nextLine();
            } 

            if (first.equals("r")){
                Random random = new Random();
                int num = random.nextInt();
                MyCanvas.first_turn = num%2 == 0 ? "w" : "b";
                System.out.println("You play " + (num%2 == 0 ? "white." : "black."));
            } else MyCanvas.first_turn = first;
        }

        if (use_clock){
            System.out.println("Bot: " + Chess.seconds[1]);
            System.out.println("Player: " + Chess.seconds[0]);
        }
    }

    static boolean attempt_piece_move(int old_pos, int new_pos, board_state board){
        
        if (!board.map.containsKey(old_pos)){
            System.out.println("no piece in starting square");
            return false;
        }

        if (board.turn != get_side(board.map.get(old_pos))){
            System.out.println("wrong turn");
            return false;
        } 
        // is move valid, must be attacking if piece in the way
        if(!is_move_valid(old_pos,new_pos,board,board.map.containsKey(new_pos))){
            System.out.println("move invalid");
            return false;
        }
        // does move leave king in check
        if (does_move_leave_in_check(old_pos, new_pos, board)){
            System.out.println("move leaves in check");
            return false;
        } 
        live_board = move_piece(old_pos, new_pos,live_board);
        return true;
    }

    static String pos_to_notation(int pos){
        return "" + (char) (pos/10 + 97) + (char) (8 - pos%10 + '0');
    }

    static void get_piece_images(){
        Map<Character, String> fenPieces = new HashMap<>() {{
            put('K', "white_king");
            put('Q', "white_queen");
            put('R', "white_rook");
            put('B', "white_bishop");
            put('N', "white_knight");
            put('P', "white_pawn");
            put('k', "black_king");
            put('q', "black_queen");
            put('r', "black_rook");
            put('b', "black_bishop");
            put('n', "black_knight");
            put('p', "black_pawn");
        }};        
        for (Map.Entry<Character,String> piece : fenPieces.entrySet()){
            try {
            // Load the image
            BufferedImage originalImage = ImageIO.read(new File(directory + "/resources/" + piece.getValue() + ".png"));
            // Resize the image
            Image image = originalImage.getScaledInstance(box_size,box_size, Image.SCALE_SMOOTH);
            piece_images.put(piece.getKey(),image);
            } catch (IOException ex) {
                // Handle the exception
            }
        }
    }

    static char swap_turn(char player){
        return player == 'w' ? 'b' : 'w';
    }
 
    static char check_for_mate(board_state board){
        int king = -1;
        for (int point : board.map.keySet()){
            if (get_allowed_moves(point, board,false)[0] != -1 && get_side(board.map.get(point)) == board.turn){
                return 'o';
            } else if (board.map.get(point) == (board.turn == 'w' ? 'K' : 'k')){
                king = point;
            }
        }
        
        for (int attacker : board.map.keySet()){
            if (is_move_valid(attacker, king, board, true) && get_side(board.map.get(attacker)) != board.turn){
                return 'c';
            }
        }

        return 's';
    }

    static boolean does_move_leave_in_check(int old_pos,int new_pos, board_state board){
        board_state board_with_move = move_piece(old_pos, new_pos, board);
        return check_checker(board_with_move,false);
    }

    static boolean is_move_valid(int old_pos, int new_pos, board_state board, boolean attacking){
        boolean move_validity = false;
        if (!board.map.containsKey(old_pos)){
            System.out.println("no origin piece");
            return false;
        }
        // Trying to move onto own piece (white to white)
        try {    
            if (board.map.containsKey(new_pos) && get_side(board.map.get(new_pos)) == get_side(board.map.get(old_pos))){
                return false;
            }
        } catch (java.lang.NullPointerException e){
            for (char piece : board.map.values()){
                if (piece == '\u0000'){
                    System.out.println("piece is null");
                } else {
                    System.out.println(piece);
                }
            }
            e.printStackTrace();
            for (int[] move : board.history){
                System.out.print(Chess.pos_to_notation(move[0]) + " " + Chess.pos_to_notation(move[1]) + " | ");
            }
            System.out.println("\n");
        }

        switch (board.map.get(old_pos)) {
            case 'P' -> move_validity = white_pawn_move(old_pos,new_pos,board,attacking);
            case 'p' -> move_validity = black_pawn_move(old_pos,new_pos,board,attacking);
            case 'R', 'r' -> move_validity = rook_move(old_pos,new_pos,board.map);
            case 'N', 'n' -> move_validity = knight_move(old_pos,new_pos,board.map);
            case 'Q', 'q' -> move_validity = queen_move(old_pos,new_pos,board.map);
            case 'K' -> move_validity = white_king_move(old_pos,new_pos,board,attacking);
            case 'k' -> move_validity = black_king_move(old_pos,new_pos,board,attacking);
            case 'B', 'b' -> move_validity = bishop_move(old_pos, new_pos,board.map);
        }

        return move_validity;
    }

    static int[] get_attackers_defenders(int pos, board_state board){
        int[] attackers_defenders = {0,0};
        for (int attacker : board.map.keySet()){
            if (is_move_valid(attacker, pos, board,true)){
                if (get_side(board.map.get(attacker)) == board.turn){
                    attackers_defenders[0]++;
                } else {
                    attackers_defenders[1]++;
                }
            }
        }
        return attackers_defenders;
    }

    static boolean check_checker(board_state board,boolean player_that_moved){
        int king = -1;
        if (player_that_moved){
            for (Map.Entry<Integer,Character> entry : board.map.entrySet()){
                if (entry.getValue() == (board.turn == 'w' ? 'K' : 'k')){
                    king = entry.getKey();
                    break;
                }
            }
            return get_attackers_defenders(king, board)[1] != 0;
        } else {
                for (Map.Entry<Integer,Character> entry : board.map.entrySet()){
                    if (entry.getValue().equals(swap_turn(board.turn) == 'w' ? 'K' : 'k')){
                        king = entry.getKey();
                        break;
                    }
                }
            return get_attackers_defenders(king, board)[0] != 0;
        }
        
    }

    static boolean rook_move(int old_pos, int new_pos,Map<Integer,Character> this_board){
        if(old_pos/10 == new_pos/10){
            int step = old_pos%10 > new_pos%10 ? -1 : 1;
            boolean piece_in_the_way = false;
            for(int i = old_pos%10 + step; i != new_pos%10; i += step){
                if (this_board.containsKey(old_pos - old_pos%10 + i)){
                    piece_in_the_way = true;
                }
            }
            if (!piece_in_the_way){
                return true;
            }
        }
        if(old_pos%10 == new_pos%10){
            int step = old_pos > new_pos ? -1 : 1;
            boolean piece_in_the_way = false;
            for(int i = old_pos/10 + step; i != new_pos/10; i += step){
                if (this_board.containsKey(old_pos%10 + i * 10)){
                    piece_in_the_way = true;
                }
            }
            if (!piece_in_the_way){
                return true;
            }
        }
        return false;
    }

    static boolean bishop_move(int old_pos, int new_pos,Map<Integer,Character> this_board){
        if(Math.abs(old_pos/10-new_pos/10) == Math.abs(old_pos%10-new_pos%10)){
            int horz_step = old_pos/10 > new_pos/10 ? -1 : 1;
            int vert_step = old_pos%10 > new_pos%10 ? -1 : 1;
            boolean piece_in_the_way = false;
            for(int i = 1; i < Math.abs(old_pos/10-new_pos/10); i++){
                if (this_board.containsKey(old_pos + (i * horz_step) * 10 + i * vert_step)){
                    piece_in_the_way = true;
                }
            }
            if (!piece_in_the_way){
                return true;
            }
        }
        return false;
    }

    static boolean queen_move(int old_pos, int new_pos,Map<Integer,Character> this_board){
        return rook_move(old_pos,new_pos,this_board) || bishop_move(old_pos, new_pos,this_board);
    }

    static boolean white_king_move(int old_pos, int new_pos,board_state this_board,boolean attacking){
        if (old_pos == 47 && !attacking){
            if (new_pos == 27 && this_board.can_castle[1] && 
            !this_board.map.containsKey(37) &&
            !this_board.map.containsKey(27) &&
            !this_board.map.containsKey(17) &&
            get_attackers_defenders(47, this_board)[1] == 0 &&
            get_attackers_defenders(37, this_board)[1] == 0 &&
            get_attackers_defenders(27, this_board)[1] == 0 &&
            get_attackers_defenders(7, this_board)[1] == 0){
                return true;
            }
            if (new_pos == 67 && this_board.can_castle[0] && 
            !this_board.map.containsKey(57) &&
            !this_board.map.containsKey(67) &&
            get_attackers_defenders(47, this_board)[1] == 0 &&
            get_attackers_defenders(57, this_board)[1] == 0 &&
            get_attackers_defenders(67, this_board)[1] == 0 &&
            get_attackers_defenders(77, this_board)[1] == 0){
                return true;
            }
        }
        return Math.abs(old_pos/10-new_pos/10) <= 1 && Math.abs(old_pos%10-new_pos%10) <= 1;
    }

    static boolean black_king_move(int old_pos, int new_pos,board_state this_board, boolean attacking){
        if (old_pos == 40 && !attacking){
            if (new_pos == 20 && this_board.can_castle[2] && 
            !this_board.map.containsKey(30) &&
            !this_board.map.containsKey(20) &&
            !this_board.map.containsKey(10) &&
            get_attackers_defenders(40, this_board)[1] == 0 &&
            get_attackers_defenders(30, this_board)[1] == 0 &&
            get_attackers_defenders(20, this_board)[1] == 0 &&
            get_attackers_defenders(0, this_board)[1] == 0){
                return true;
            }
            if (new_pos == 60 && this_board.can_castle[3] && 
            !this_board.map.containsKey(50) &&
            !this_board.map.containsKey(60) &&
            get_attackers_defenders(40, this_board)[1] == 0 &&
            get_attackers_defenders(50, this_board)[1] == 0 &&
            get_attackers_defenders(60, this_board)[1] == 0 &&
            get_attackers_defenders(70, this_board)[1] == 0){
                return true;
            }
        }
        return Math.abs(old_pos/10-new_pos/10) <= 1 && Math.abs(old_pos%10-new_pos%10) <= 1;
    }

    static boolean black_pawn_move(int old_pos, int new_pos,board_state board,boolean attacking){
        return (old_pos/10 == new_pos/10 && old_pos%10 + 1 == new_pos%10 && !board.map.containsKey(new_pos)) && !attacking || (old_pos/10 == new_pos/10 && old_pos%10 + 2 == new_pos%10 && old_pos%10 == 1 && !board.map.containsKey(old_pos+2) && !board.map.containsKey(old_pos+1)) && !attacking || (Math.abs(old_pos/10-new_pos/10) == 1 && old_pos%10 + 1 == new_pos%10 && (board.map.containsKey(new_pos) || board.en_passent_square == new_pos));
    }

    static boolean white_pawn_move(int old_pos, int new_pos,board_state board,boolean attacking){
        return (old_pos/10 == new_pos/10 && old_pos%10 - 1 == new_pos%10 && !board.map.containsKey(new_pos)) && !attacking || (old_pos/10 == new_pos/10 && old_pos%10 - 2 == new_pos%10 && old_pos%10 == 6 && !board.map.containsKey(old_pos-2) && !board.map.containsKey(old_pos-1)) && !attacking || (Math.abs(old_pos/10-new_pos/10) == 1 && old_pos%10 - 1 == new_pos%10 && (board.map.containsKey(new_pos) || board.en_passent_square == new_pos));
    }

    static boolean knight_move(int old_pos, int new_pos,Map<Integer,Character> this_board){
        return (Math.abs(old_pos/10-new_pos/10) == 1 && Math.abs(old_pos%10-new_pos%10) == 2) || (Math.abs(old_pos/10-new_pos/10) == 2 && Math.abs(old_pos%10-new_pos%10) == 1);
    }

    static int[] get_allowed_moves(int point,board_state board,boolean attacking){
        int[] moves = new int[27];
        int index = 0;
        for (int n = 0; n < 27; n++){
            moves[n] = -1;
        }
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++){
                if (is_move_valid(point, i * 10 + j,board,attacking) && !does_move_leave_in_check(point, i * 10 + j, board)){
                    moves[index] = i * 10 + j; 
                    index++;
                }
            }
        }
        return moves;
    }

    static char get_side(char piece){
        return Character.isLowerCase(piece) ? 'b' : 'w';
    }

    static boolean[] update_castling_rights(int old_pos, int new_pos, board_state board){
        boolean[] can_castle = new boolean[4];
        System.arraycopy(board.can_castle,0,can_castle,0,4);
        char piece = board.map.get(old_pos);
        switch (piece) {
            case 'K' -> {
                can_castle[0] = false;
                can_castle[1] = false;
            }
            case 'k' -> {
                can_castle[2] = false;
                can_castle[3] = false;
            }
            case 'R' -> {
                if (old_pos == 7) {
                    can_castle[1] = false;
                } else if (old_pos == 77) {
                    can_castle[0] = false;
                }
            }
            case 'r' -> {
                if (old_pos == 0) {
                    can_castle[2] = false;
                } else if (old_pos == 70) {
                    can_castle[3] = false;
                }
            }
            default -> {
            }
        }
        // Handle other cases if needed
        return can_castle;
    }

    static board_state move_piece(int old_pos, int new_pos,board_state board){ 
        HashMap<Integer,Character> new_map = new HashMap<>(board.map);
        ArrayList<int[]> new_history = new ArrayList<>(board.history);
        ArrayList<board_state> new_board_history = new ArrayList<>(board.board_history);
        int new_en_passent_square = -1;
        int[] new_king_pos = Arrays.copyOf(board.king_pos, board.king_pos.length);

        char piece = new_map.remove(old_pos);
        // promotion
        if (piece == 'P' && new_pos%10 == 0) new_map.put(new_pos,'Q');
        else if (piece == 'p' && new_pos%10 == 7) new_map.put(new_pos,'q');
        // castling
        else if((piece == 'K' || piece == 'k')){
            new_king_pos[piece == 'K' ? 0 : 1] = new_pos;
            if (Math.abs(new_pos-old_pos) == 20){
            char rook_name = new_map.remove((old_pos < new_pos ? 70 : 0) + new_pos%10); 
            new_map.put((new_pos - old_pos) / 2 + old_pos,rook_name);
            }
            new_map.put(new_pos,piece);
        // use en passent
        } else if ((piece == 'P' || piece == 'p') && new_pos == board.en_passent_square){
            new_map.put(new_pos,piece);
            new_map.remove(old_pos%10 + board.en_passent_square - board.en_passent_square%10);
        // set en passent
        } else if ((piece == 'P' || piece == 'p') && Math.abs(new_pos - old_pos) == 2){
            new_en_passent_square = (new_pos%10 - old_pos%10) / 2 + old_pos;
            new_map.put(new_pos,piece);
        } else {
            new_map.put(new_pos,piece);
        }

        int[] move = {old_pos,new_pos};
        new_history.add(move);
        new_board_history.add(board);
        
        return new board_state(new_map,update_castling_rights(old_pos, new_pos, board),new_en_passent_square,swap_turn(board.turn),new_history,new_board_history,new_king_pos);
            
    }

    static board_state read_FEN(String FEN){
        Map<Integer,Character> map = new HashMap<>(); 
        boolean[] can_castle = new boolean[4];
        int en_passent_square = -1;
        char turn;
        ArrayList<int[]> history = new ArrayList<>(); 
        ArrayList<board_state> board_history = new ArrayList<>(); 
        int[] king_pos = new int[2];

        int board_index = 0;
        int code_index = 0;
        boolean halt = false;
        char[] code = FEN.toCharArray();
        while (!halt && code_index < FEN.length()){
            switch(code[code_index]) {
                case 'Q', 'R', 'B', 'N', 'P', 'q', 'r', 'b', 'n', 'p' -> {
                    map.put(board_index, code[code_index]);
                    board_index += 10;
                }
                case 'K','k' -> {
                    king_pos[code[code_index] == 'K' ? 0 : 1] = board_index;
                    map.put(board_index, code[code_index]);
                    board_index += 10;
                }
                case '1', '2', '3', '4', '5', '6', '7', '8' -> board_index += 10 * (int) Character.getNumericValue(code[code_index]);
                case '/' -> board_index = board_index%10 + 1;
                case ' ' -> halt = true;
            }
            code_index++;
        }
        
        turn = code[code_index];

        code_index += 2;
        
        for (int i = code_index; i < code_index + 4; i++){
            switch(code[i]){
                case 'K' -> can_castle[0] = true;
                case 'Q' -> can_castle[1] = true;
                case 'k' -> can_castle[2] = true;
                case 'q' -> can_castle[3] = true;
            }
        }
        return new board_state(map,can_castle,en_passent_square,turn,history,board_history,king_pos);
    }

}

class board_state {
    public Map<Integer,Character> map; 
    public boolean[] can_castle; 
    public int en_passent_square = -1; 
    public char turn; 
    public ArrayList<int[]> history; 
    public double eval = 0;
    public ArrayList<board_state> board_history;
    public int[] king_pos = new int[2];

    public board_state(Map<Integer,Character> inp_map, boolean[] inp_can_castle, int inp_en_passent_square, char inp_turn, ArrayList<int[]> inp_history, ArrayList<board_state> inp_board_history, int[] inp_king_pos) {
        this.map = new HashMap<>(inp_map);
        this.can_castle = new boolean[4];
        System.arraycopy(inp_can_castle,0,this.can_castle,0,4);
        this.turn = inp_turn;
        this.en_passent_square = inp_en_passent_square;
        this.history = new ArrayList<>(inp_history);
        this.board_history = new ArrayList<>(inp_board_history);
        System.arraycopy(inp_king_pos,0,this.king_pos,0,2);
    }

    @Override
    public int hashCode() {
        int hash = 1;
        hash = 31 * hash + (this.map != null ? this.map.hashCode() : 0);
        hash = 31 * hash + turn;
        hash = 31 * hash + this.en_passent_square;
        hash = 31 * hash + Arrays.hashCode(this.can_castle);
        return hash;
    }

    @Override
    public boolean equals(Object input){
        if (this == input)
            return true;
        if (input == null || getClass() != input.getClass())
            return false;

        board_state input_board = (board_state) input;

        if (en_passent_square != input_board.en_passent_square || turn != input_board.turn || !Arrays.equals(can_castle, input_board.can_castle))
            return false;
        
        if (map == null && input_board.map == null)
            return true; 
        if (map == null || input_board.map == null)
            return false; 
        
        return map.equals(input_board.map);
    }
}

class MyCanvas extends Canvas implements MouseListener, KeyListener{
    private BufferedImage offscreenImage;
    private Graphics offscreenGraphics;
    private BufferedImage background;
    private Graphics bg;
    private int piece_selected = -1;
    private int turn = -1;
    public static String first_turn;
    private long last_move_time;
    private final int[] circles_to_draw = new int[27];
    private final int[] highlight_squares = {-1,-1};
    private boolean flip_board = false;
    
    public MyCanvas(){
        for (int i = 0; i < 27; i++){
            circles_to_draw[i] = -1;
        }

        listener_initialization();

        if (first_turn.equals("b")){
            bot_move();
        }
    }

    public final void listener_initialization(){
        addMouseListener(this);
        addKeyListener(this);
    }

    @Override
    public void paint(Graphics g){
        if (offscreenImage == null){
            offscreenImage = new BufferedImage(Chess.canvas_size,Chess.canvas_size, BufferedImage.TYPE_INT_ARGB);
            offscreenGraphics = offscreenImage.getGraphics();
            background = new BufferedImage(Chess.canvas_size,Chess.canvas_size, BufferedImage.TYPE_INT_ARGB);
            bg = background.getGraphics();
            bg.setColor(getBackground());
            draw_squares(bg);
            repaint();
        }

        offscreenGraphics.drawImage(background,0,0,this);

        draw_highlight_squares(offscreenGraphics);

        draw_move_options(offscreenGraphics);

        draw_pieces(offscreenGraphics);

        g.drawImage(offscreenImage,0,0,this);

    }

    public void draw_move_options(Graphics g){
        for (int point : circles_to_draw){
            if ((point%10 + point/10) % 2 == 0){
                g.setColor(Chess.option_colour[1]);
            } else {
                g.setColor(Chess.option_colour[0]);
            }
            if (flip_board){
                if (Chess.live_board.map.containsKey(point)){
                    g.fillOval((7 - point/10) * Chess.box_size, (7 - point%10) * Chess.box_size, Chess.box_size, Chess.box_size);
                    if ((point%10 + point/10) % 2 == 0){
                        g.setColor(Chess.light_squares);
                    } else {
                        g.setColor(Chess.dark_squares);
                    }
                    g.fillOval((7 - point/10) * Chess.box_size + Chess.box_size / 8, (7 - point%10) * Chess.box_size + Chess.box_size / 8, Chess.box_size * 3 / 4, Chess.box_size * 3 / 4);
                } else {    
                    g.fillOval((7 - point/10) * Chess.box_size + Chess.box_size / 3, (7 - point%10) * Chess.box_size + Chess.box_size / 3, Chess.box_size / 3, Chess.box_size / 3);
                }
            } else {
                if (Chess.live_board.map.containsKey(point)){
                    g.fillOval(point/10 * Chess.box_size, point%10 * Chess.box_size, Chess.box_size, Chess.box_size);
                    if ((point%10 + point/10) % 2 == 0){
                        g.setColor(Chess.light_squares);
                    } else {
                        g.setColor(Chess.dark_squares);
                    }
                    g.fillOval(point/10 * Chess.box_size + Chess.box_size / 8, point%10 * Chess.box_size + Chess.box_size / 8, Chess.box_size * 3 / 4, Chess.box_size * 3 / 4);
                } else {    
                    g.fillOval(point/10 * Chess.box_size + Chess.box_size / 3, point%10 * Chess.box_size + Chess.box_size / 3, Chess.box_size / 3, Chess.box_size / 3);
                }
            }
        }
    }

    public void draw_squares(Graphics g){
        for (int i = 0; i < 8; i++){
            for (int j = 0; j < 8; j++){
                if ((i + j) % 2 == 0){
                    g.setColor(Chess.light_squares);
                } else {
                    g.setColor(Chess.dark_squares);
                }
                g.fillRect(i*Chess.box_size, j*Chess.box_size, Chess.box_size, Chess.box_size);
            }
        }
    }

    public void draw_highlight_squares(Graphics g){
        if (!flip_board){
            g.setColor(Chess.highlight[1]);
            g.fillRect(highlight_squares[0]/10*Chess.box_size, highlight_squares[0]%10*Chess.box_size, Chess.box_size, Chess.box_size);
            g.setColor(Chess.highlight[0]);
            g.fillRect(highlight_squares[1]/10*Chess.box_size, highlight_squares[1]%10*Chess.box_size, Chess.box_size, Chess.box_size);
        } else {
            g.setColor(Chess.highlight[1]);
            g.fillRect((7 - highlight_squares[0]/10)*Chess.box_size, (7 - highlight_squares[0]%10)*Chess.box_size, Chess.box_size, Chess.box_size);
            g.setColor(Chess.highlight[0]);
            g.fillRect((7 - highlight_squares[1]/10)*Chess.box_size, (7 - highlight_squares[1]%10)*Chess.box_size, Chess.box_size, Chess.box_size);
        }
    
    }

    public void draw_pieces(Graphics g){
        for (Map.Entry<Integer,Character> piece : Chess.live_board.map.entrySet()){
            Image piece_image = Chess.piece_images.get(piece.getValue());
            if (flip_board){
                g.drawImage(piece_image,(7-piece.getKey()/10)*Chess.box_size,(7-piece.getKey()%10)*Chess.box_size,this);
            } else {
                g.drawImage(piece_image,piece.getKey()/10*Chess.box_size,piece.getKey()%10*Chess.box_size,this);
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e){
        if (e.getButton() == MouseEvent.BUTTON3){
            flip_board = !flip_board;
            repaint();
            return;
        }

        int mouse_point;
        if (flip_board){
            mouse_point = 10 * (7 - e.getX() / Chess.box_size) + 7 - e.getY() / Chess.box_size;
        } else {
            mouse_point = 10 * (e.getX() / Chess.box_size) + e.getY() / Chess.box_size;
        }

        if (piece_selected != -1){
            if (Chess.live_board.map.containsKey(mouse_point) && Chess.get_side(Chess.live_board.map.get(mouse_point)) == Chess.live_board.turn){
                select_piece(mouse_point);
                return;
            }

            boolean move_success = Chess.attempt_piece_move(piece_selected,mouse_point,Chess.live_board);
            if (!move_success){
                return;
            }
            else {
                char game_state = Chess.check_for_mate(Chess.live_board);
                if (game_state != 'o'){
                    switch (game_state) {
                        case 'c' -> System.out.println("Checkmate");
                        case 's' -> System.out.println("Stalemate");
                        default -> System.out.println("Error reading game result");
                    }
                    for (int i = 0; i < 27; i++){
                        circles_to_draw[i] = -1;
                    }
                    repaint();
                    return;
                }
            } 

            piece_selected = -1;
            highlight_squares[1] = mouse_point;
            for (int i = 0; i < 27; i++){
                circles_to_draw[i] = -1;
            }

            repaint();
            swap_clock();
            bot_move();
            
            
        } else {
            select_piece(mouse_point);
        }
    }

    public void swap_clock(){
        if (!Chess.use_clock) return;
        if (turn == -1){
            turn = first_turn.equals("w") ? 1 : 0;
            last_move_time = System.currentTimeMillis();
            return;
        }
        long now = System.currentTimeMillis();
        long time_to_move = now - last_move_time;
        last_move_time = now;
        Chess.seconds[turn] -= (int) time_to_move / 1000.0;

        if (Chess.seconds[0] < 0){
            System.out.println("Player out of time.");
        }

        if (Chess.seconds[1] < 0){
            System.out.println("Bot out of time.");
        }


        turn ^= 1;
        Chess.seconds[turn] += Chess.increment;

        
        System.out.println("\n\n_______________");
        System.out.println("Bot     | " + (int) Chess.seconds[1]/60 + " m " + String.format("%.2f",Chess.seconds[1]%60)  + " s");
        System.out.println("Player  | " + (int) Chess.seconds[0]/60 + " m " + String.format("%.2f",Chess.seconds[0]%60) + " s");
        System.out.println("_______________\n\n");
    }

    public void bot_move(){
        SwingUtilities.invokeLater(() -> {
            // Update chessboard state (if needed)
            repaint();
            // After the repaint, start the move search in another thread
            new Thread(() -> {
                int[] best_move = Chess_Bot.get_bot_move(Chess.live_board);
                SwingUtilities.invokeLater(() -> {
                    Chess.attempt_piece_move(best_move[0], best_move[1], Chess.live_board);
                    swap_clock();
                    highlight_squares[0] = best_move[0];
                    highlight_squares[1] = best_move[1];


                    char game_state = Chess.check_for_mate(Chess.live_board);
                    if (game_state != 'o'){
                        switch (game_state) {
                            case 'c' -> System.out.println("Checkmate");
                            case 's' -> System.out.println("Stalemate");
                            default -> System.out.println("Error reading game result");
                        }
                    }
                    repaint();
                });
            }).start();
        });
    }

    public void select_piece(int mouse_point){
        for (int point : Chess.live_board.map.keySet()){
            if (mouse_point == point && Chess.get_side(Chess.live_board.map.get(mouse_point)) == Chess.live_board.turn) {
                highlight_squares[1] = -1;
                highlight_squares[0] = point;
                piece_selected = point;
                for (int i = 0; i < 27; i++){
                    circles_to_draw[i] = -1;
                }
                int[] allowed_moves = Chess.get_allowed_moves(piece_selected,Chess.live_board,false);
                System.arraycopy(allowed_moves,0,circles_to_draw,0,27);
                repaint();
            }
        }
    }

    @Override
    public void keyPressed(KeyEvent e){
        if (Chess.use_clock){
            System.out.println("Bot: " + (int) Chess.seconds[1]/60 + " m " + String.format("%.2f",Chess.seconds[1]%60)  + " s");
            System.out.println("Player: " + (int) Chess.seconds[0]/60 + " m " + String.format("%.2f",Chess.seconds[0]%60) + " s");
        } else {
            bot_move();
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        // Do nothing
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        // Do nothing
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        // Do nothing
    }

    @Override
    public void mouseExited(MouseEvent e) {
        // Do nothing
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // Do nothing
    }

    @Override
    public void keyReleased(KeyEvent e) {
        // Do nothing
    }
}
